package dev.cloudpilot.simulator;

import java.time.Duration;
import java.time.Instant;

public record ActiveScenario(ScenarioType type, Instant startedAt, Instant endsAt, String startedBy) {

    /** 0.0 when it starts, 1.0 when it ends. */
    public double progress(Instant now) {
        double total = Duration.between(startedAt, endsAt).toMillis();
        double elapsed = Duration.between(startedAt, now).toMillis();
        return Math.max(0.0, Math.min(1.0, elapsed / total));
    }

    public boolean isOver(Instant now) {
        return !now.isBefore(endsAt);
    }
}
