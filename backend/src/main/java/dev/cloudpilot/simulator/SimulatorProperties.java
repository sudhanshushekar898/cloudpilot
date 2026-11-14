package dev.cloudpilot.simulator;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simulator settings (cloudpilot.simulator.*).
 *
 * @param enabled  generate metrics on a schedule (off in integration tests)
 * @param interval time between samples
 * @param backfill history to generate on startup when the database is empty, so charts aren't blank
 */
@ConfigurationProperties(prefix = "cloudpilot.simulator")
public record SimulatorProperties(boolean enabled, Duration interval, Duration backfill) {

    public SimulatorProperties {
        if (interval == null) {
            interval = Duration.ofSeconds(10);
        }
        if (backfill == null) {
            backfill = Duration.ofHours(1);
        }
    }
}
