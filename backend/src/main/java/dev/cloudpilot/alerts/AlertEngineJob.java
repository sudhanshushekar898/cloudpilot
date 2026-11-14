package dev.cloudpilot.alerts;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cloudpilot.alerts", name = "engine-enabled", havingValue = "true", matchIfMissing = true)
public class AlertEngineJob {

    private final AlertEngine engine;
    private final Clock clock;

    public AlertEngineJob(AlertEngine engine, Clock clock) {
        this.engine = engine;
        this.clock = clock;
    }

    /** Every 30 s: fast enough that a 2-minute rule fires within half a minute of qualifying. */
    @Scheduled(fixedRateString = "${cloudpilot.alerts.interval:30s}", initialDelayString = "15s")
    public void run() {
        engine.evaluate(Instant.now(clock));
    }
}
