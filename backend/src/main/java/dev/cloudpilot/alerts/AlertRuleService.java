package dev.cloudpilot.alerts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.cloudpilot.common.ConflictException;
import dev.cloudpilot.common.NotFoundException;
import dev.cloudpilot.metrics.MetricType;
import dev.cloudpilot.resources.ResourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertRuleService {

    private final AlertRuleRepository rules;
    private final AlertRepository alerts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AlertRuleService(AlertRuleRepository rules, AlertRepository alerts, ApplicationEventPublisher events,
                            Clock clock) {
        this.rules = rules;
        this.alerts = alerts;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RuleView> list() {
        return rules.findAllByOrderByNameAsc().stream().map(RuleView::of).toList();
    }

    @Transactional
    public RuleView create(RuleRequest request) {
        if (rules.existsByNameIgnoreCase(request.name().trim())) {
            throw new ConflictException("A rule named '" + request.name().trim() + "' already exists");
        }
        AlertRule rule = new AlertRule(request.name().trim(), request.metric(), request.operator(), request.threshold(),
                Duration.ofMinutes(request.durationMinutes()), request.severity(), request.resourceType(),
                Instant.now(clock));
        if (!request.enabled()) {
            rule.update(rule.getName(), rule.getMetric(), rule.getOperator(), rule.getThreshold(), rule.getDuration(),
                    rule.getSeverity(), rule.getResourceType(), false, Instant.now(clock));
        }
        return RuleView.of(rules.save(rule));
    }

    @Transactional
    public RuleView update(UUID id, RuleRequest request) {
        AlertRule rule = rules.findById(id).orElseThrow(() -> new NotFoundException("No alert rule with id " + id));
        String name = request.name().trim();
        if (!rule.getName().equalsIgnoreCase(name) && rules.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A rule named '" + name + "' already exists");
        }
        Instant now = Instant.now(clock);
        rule.update(name, request.metric(), request.operator(), request.threshold(),
                Duration.ofMinutes(request.durationMinutes()), request.severity(), request.resourceType(),
                request.enabled(), now);

        // A disabled rule can't confirm its alerts are still valid, so close them rather than
        // leave them firing forever.
        if (!request.enabled()) {
            for (Alert alert : alerts.findByRuleIdAndStatus(id, Alert.Status.FIRING)) {
                alert.resolve(now);
                events.publishEvent(new AlertResolved(alert.getId(), id, alert.getResourceId(), now));
            }
        }
        return RuleView.of(rule);
    }

    public record RuleRequest(
            @NotBlank @Size(min = 3, max = 100) String name,
            @NotNull MetricType metric,
            @NotNull Operator operator,
            double threshold,
            @Min(1) @Max(60) int durationMinutes,
            @NotNull Severity severity,
            ResourceType resourceType,
            boolean enabled) {
    }

    public record RuleView(UUID id, String name, MetricType metric, Operator operator, double threshold,
                           long durationMinutes, Severity severity, ResourceType resourceType, boolean enabled,
                           Instant updatedAt) {

        static RuleView of(AlertRule rule) {
            return new RuleView(rule.getId(), rule.getName(), rule.getMetric(), rule.getOperator(), rule.getThreshold(),
                    rule.getDuration().toMinutes(), rule.getSeverity(), rule.getResourceType(), rule.isEnabled(),
                    rule.getUpdatedAt());
        }
    }
}
