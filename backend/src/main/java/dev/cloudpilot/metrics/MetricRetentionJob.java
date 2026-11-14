package dev.cloudpilot.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes old samples every night so metric_samples stays small. */
@Component
public class MetricRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MetricRetentionJob.class);

    private final MetricStore store;
    private final EventStore events;
    private final Clock clock;
    private final Duration retention;

    public MetricRetentionJob(MetricStore store, EventStore events, Clock clock,
                              @Value("${cloudpilot.metrics.retention:7d}") Duration retention) {
        this.store = store;
        this.events = events;
        this.clock = clock;
        this.retention = retention;
    }

    /** Deployments are kept: they are few, and old ones explain recurring failures. */
    @Scheduled(cron = "${cloudpilot.metrics.retention-cron:0 30 3 * * *}")
    public void purge() {
        Instant cutoff = Instant.now(clock).minus(retention);
        int samples = store.deleteOlderThan(cutoff);
        int logs = events.deleteLogsOlderThan(cutoff);
        log.info("Retention removed {} samples and {} log lines older than {}", samples, logs, retention);
    }
}
