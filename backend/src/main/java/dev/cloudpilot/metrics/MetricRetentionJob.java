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
    private final Clock clock;
    private final Duration retention;

    public MetricRetentionJob(MetricStore store, Clock clock,
                              @Value("${cloudpilot.metrics.retention:7d}") Duration retention) {
        this.store = store;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${cloudpilot.metrics.retention-cron:0 30 3 * * *}")
    public void purge() {
        int deleted = store.deleteOlderThan(Instant.now(clock).minus(retention));
        log.info("Metric retention removed {} samples older than {}", deleted, retention);
    }
}
