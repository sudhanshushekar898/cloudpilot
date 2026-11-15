package dev.cloudpilot.alerts;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.cloudpilot.metrics.MetricStore;
import dev.cloudpilot.metrics.MetricType;
import dev.cloudpilot.metrics.WindowStats;
import dev.cloudpilot.resources.CloudResource;
import dev.cloudpilot.resources.CloudResourceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates every enabled rule against every resource in its scope. Called on a schedule
 * by {@link AlertEngineJob}; takes the time as a parameter so tests control it exactly.
 */
@Service
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

    /** A firing alert resolves only after this long with every sample back to normal. */
    static final Duration RESOLVE_WINDOW = Duration.ofSeconds(60);

    /** Samples arrive every 10 s; allow a few to be late or missing at the window start. */
    private final RuleEvaluator evaluator = new RuleEvaluator(Duration.ofSeconds(30));

    private final AlertRuleRepository rules;
    private final AlertRepository alerts;
    private final CloudResourceRepository resources;
    private final MetricStore metrics;
    private final ApplicationEventPublisher events;
    private final MeterRegistry registry;
    private final Timer runTimer;

    public AlertEngine(AlertRuleRepository rules, AlertRepository alerts, CloudResourceRepository resources,
                       MetricStore metrics, ApplicationEventPublisher events, MeterRegistry registry) {
        this.rules = rules;
        this.alerts = alerts;
        this.resources = resources;
        this.metrics = metrics;
        this.events = events;
        this.registry = registry;
        // If one engine run gets close to the 30 s interval, runs start to overlap.
        this.runTimer = Timer.builder("cloudpilot.alerts.engine.run")
                .description("Time to evaluate every enabled rule once")
                .register(registry);
    }

    @Transactional
    public void evaluate(Instant now) {
        runTimer.record(() -> evaluateRules(now));
    }

    private void evaluateRules(Instant now) {
        List<CloudResource> allResources = resources.findAll();
        // Upper bound is exclusive in the query; include samples recorded exactly at "now".
        Instant to = now.plusMillis(1);

        for (AlertRule rule : rules.findByEnabledTrue()) {
            Instant windowStart = now.minus(rule.getDuration());
            Map<UUID, WindowStats> window = metrics.windowStats(rule.getMetric(), windowStart, to);
            Map<UUID, WindowStats> recent = metrics.windowStats(rule.getMetric(), now.minus(RESOLVE_WINDOW), to);
            Map<UUID, Alert> firing = alerts.findByRuleIdAndStatus(rule.getId(), Alert.Status.FIRING).stream()
                    .collect(Collectors.toMap(Alert::getResourceId, Function.identity()));

            for (CloudResource resource : allResources) {
                if (!rule.appliesTo(resource.getType())) {
                    continue;
                }
                Alert current = firing.get(resource.getId());
                RuleEvaluator.Decision decision = evaluator.decide(rule.condition(),
                        Optional.ofNullable(window.get(resource.getId())),
                        Optional.ofNullable(recent.get(resource.getId())),
                        current != null, windowStart);

                switch (decision) {
                    case FIRE -> fire(rule, resource, window.get(resource.getId()), now);
                    case RESOLVE -> resolve(current, now);
                    case NONE -> { }
                }
            }
        }
    }

    private void fire(AlertRule rule, CloudResource resource, WindowStats stats, Instant now) {
        String message = describe(rule, resource, stats.latestValue());
        Alert alert = alerts.save(new Alert(rule.getId(), resource.getId(), rule.getSeverity(), message,
                stats.latestValue(), now));
        log.info("Alert fired: {}", message);
        registry.counter("cloudpilot.alerts.fired", "severity", rule.getSeverity().name(),
                "metric", rule.getMetric().name()).increment();
        events.publishEvent(new AlertFired(alert.getId(), rule.getId(), resource.getId(), rule.getSeverity(),
                message, now));
    }

    private void resolve(Alert alert, Instant now) {
        alert.resolve(now);
        log.info("Alert resolved: {}", alert.getMessage());
        registry.counter("cloudpilot.alerts.resolved", "severity", alert.getSeverity().name()).increment();
        events.publishEvent(new AlertResolved(alert.getId(), alert.getRuleId(), alert.getResourceId(), now));
    }

    /** e.g. "checkout-api error rate 18% > 5% for 5 min". */
    static String describe(AlertRule rule, CloudResource resource, double value) {
        MetricType metric = rule.getMetric();
        return "%s %s %s %s %s for %d min".formatted(
                resource.getName(),
                metric.name().toLowerCase().replace('_', ' '),
                withUnit(value, metric),
                rule.getOperator().symbol(),
                withUnit(rule.getThreshold(), metric),
                rule.getDuration().toMinutes());
    }

    private static String withUnit(double value, MetricType metric) {
        String number = value == Math.rint(value) ? String.valueOf((long) value) : "%.1f".formatted(value);
        return metric.unit().equals("ms") ? number + " ms" : number + metric.unit();
    }
}
