package dev.cloudpilot.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.cloudpilot.metrics.MetricSample;
import dev.cloudpilot.metrics.MetricStore;
import dev.cloudpilot.resources.CloudResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Writes one batch of samples every interval. In a real deployment this is where a
 * CloudWatch or Prometheus collector would push data instead; everything downstream
 * (status, alerts, incidents) only sees {@link MetricStore}.
 */
@Component
@ConditionalOnProperty(prefix = "cloudpilot.simulator", name = "enabled", havingValue = "true")
public class SimulatorJob {

    private static final Logger log = LoggerFactory.getLogger(SimulatorJob.class);

    /** Backfilled history is coarser than live data; charts bucket it anyway. */
    private static final Duration BACKFILL_STEP = Duration.ofSeconds(30);

    private final CloudResourceRepository resources;
    private final MetricStore store;
    private final MetricGenerator generator;
    private final ScenarioManager scenarios;
    private final SimulatorProperties properties;
    private final Clock clock;

    public SimulatorJob(CloudResourceRepository resources, MetricStore store, MetricGenerator generator,
                        ScenarioManager scenarios, SimulatorProperties properties, Clock clock) {
        this.resources = resources;
        this.store = store;
        this.generator = generator;
        this.scenarios = scenarios;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${cloudpilot.simulator.interval:10s}", initialDelayString = "5s")
    public void tick() {
        store.saveAll(generator.generate(loadResources(), Instant.now(clock), scenarios.active()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillIfEmpty() {
        Instant now = Instant.now(clock);
        Instant from = now.minus(properties.backfill());
        if (store.hasSamplesSince(from)) {
            return;
        }
        List<SimulatedResource> topology = loadResources();
        List<MetricSample> history = new ArrayList<>();
        for (Instant at = from; at.isBefore(now); at = at.plus(BACKFILL_STEP)) {
            history.addAll(generator.generate(topology, at, Optional.empty()));
        }
        store.saveAll(history);
        log.info("Backfilled {} metric samples covering {}", history.size(), properties.backfill());
    }

    private List<SimulatedResource> loadResources() {
        return resources.findAll().stream()
                .map(r -> new SimulatedResource(r.getId(), r.getName(), r.getType()))
                .toList();
    }
}
