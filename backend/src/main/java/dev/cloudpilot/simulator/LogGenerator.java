package dev.cloudpilot.simulator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import dev.cloudpilot.metrics.LogEvent;

/**
 * Writes log lines for resources a running scenario is affecting. Normal traffic writes no
 * logs: we only keep warnings and errors, like most teams do after log-cost reviews.
 */
public class LogGenerator {

    /** Below this effect strength the problem isn't visible in logs yet. */
    private static final double MIN_STRENGTH = 0.3;

    private final Random random;

    public LogGenerator(Random random) {
        this.random = random;
    }

    public List<LogEvent> generate(List<SimulatedResource> resources, Instant at, Optional<ActiveScenario> scenario) {
        if (scenario.isEmpty()) {
            return List.of();
        }
        ScenarioType type = scenario.get().type();
        double progress = scenario.get().progress(at);
        List<LogEvent> logs = new ArrayList<>();

        for (SimulatedResource resource : resources) {
            double strength = type.effects().stream()
                    .filter(e -> e.resourceName().equals(resource.name()))
                    .mapToDouble(e -> e.ramp().factor(progress))
                    .max().orElse(0);
            List<ScenarioType.LogTemplate> templates = type.logs().stream()
                    .filter(t -> t.resourceName().equals(resource.name()))
                    .toList();
            if (strength < MIN_STRENGTH || templates.isEmpty()) {
                continue;
            }
            ScenarioType.LogTemplate template = templates.get(random.nextInt(templates.size()));
            logs.add(new LogEvent(resource.id(), template.level(), template.message(), at));
        }
        return logs;
    }
}
