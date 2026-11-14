package dev.cloudpilot.simulator;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Holds the one scenario that is currently running, if any.
 *
 * Kept in memory on purpose: it's demo state, losing it on restart is fine, and there is
 * a single backend instance. With several instances this would move to the database or
 * Redis so every instance simulates the same failure.
 */
@Component
public class ScenarioManager {

    private final Clock clock;
    private final AtomicReference<ActiveScenario> current = new AtomicReference<>();

    public ScenarioManager(Clock clock) {
        this.clock = clock;
    }

    public ActiveScenario start(ScenarioType type, String startedBy) {
        Instant now = Instant.now(clock);
        ActiveScenario scenario = new ActiveScenario(type, now, now.plus(type.duration()), startedBy);
        current.set(scenario);
        return scenario;
    }

    public void stop() {
        current.set(null);
    }

    public Optional<ActiveScenario> active() {
        ActiveScenario scenario = current.get();
        if (scenario != null && scenario.isOver(Instant.now(clock))) {
            // Only clear it if nobody started a new one in the meantime.
            current.compareAndSet(scenario, null);
            return Optional.empty();
        }
        return Optional.ofNullable(scenario);
    }
}
