package dev.cloudpilot.simulator;

import java.time.Duration;
import java.util.List;

import dev.cloudpilot.metrics.MetricType;

import static dev.cloudpilot.metrics.MetricType.AVAILABILITY;
import static dev.cloudpilot.metrics.MetricType.CPU;
import static dev.cloudpilot.metrics.MetricType.ERROR_RATE;
import static dev.cloudpilot.metrics.MetricType.LATENCY_P95;
import static dev.cloudpilot.metrics.MetricType.MEMORY;

/**
 * Failure scenarios an admin can start for a demo. Each one pushes specific metrics on
 * specific resources towards a peak value, following the dependency graph: the root cause
 * is hit hardest and the services that depend on it show knock-on symptoms. That is what
 * alert grouping (M5) and root-cause analysis (M7) have to untangle.
 */
public enum ScenarioType {

    DB_CONNECTION_EXHAUSTION(
            "Orders DB connection pool exhausted",
            "orders-db runs out of connections; checkout-api and payments-worker start timing out.",
            Duration.ofMinutes(15),
            List.of(
                    new Effect("orders-db", CPU, 97, Ramp.FAST),
                    new Effect("orders-db", LATENCY_P95, 2400, Ramp.FAST),
                    new Effect("orders-db", ERROR_RATE, 9, Ramp.FAST),
                    new Effect("checkout-api", LATENCY_P95, 3200, Ramp.FAST),
                    new Effect("checkout-api", ERROR_RATE, 18, Ramp.FAST),
                    new Effect("checkout-api", AVAILABILITY, 94, Ramp.FAST),
                    new Effect("payments-worker", ERROR_RATE, 11, Ramp.FAST),
                    new Effect("web-frontend", LATENCY_P95, 1800, Ramp.FAST),
                    new Effect("web-frontend", ERROR_RATE, 6, Ramp.FAST))),

    BAD_DEPLOYMENT(
            "Bad checkout-api deployment",
            "A new checkout-api release throws errors on most requests.",
            Duration.ofMinutes(10),
            List.of(
                    new Effect("checkout-api", ERROR_RATE, 38, Ramp.FAST),
                    new Effect("checkout-api", AVAILABILITY, 70, Ramp.FAST),
                    new Effect("checkout-api", CPU, 75, Ramp.FAST),
                    new Effect("web-frontend", ERROR_RATE, 9, Ramp.FAST))),

    QUEUE_BACKLOG(
            "Payments queue backlog",
            "payments-worker can't keep up; messages pile up in payments-queue.",
            Duration.ofMinutes(15),
            List.of(
                    new Effect("payments-queue", LATENCY_P95, 9000, Ramp.LINEAR),
                    new Effect("payments-worker", CPU, 96, Ramp.FAST),
                    new Effect("checkout-api", LATENCY_P95, 1400, Ramp.LINEAR))),

    POD_MEMORY_LEAK(
            "Memory leak in payments-worker",
            "payments-worker memory grows until pods are OOM-killed and restart.",
            Duration.ofMinutes(20),
            List.of(
                    new Effect("payments-worker", MEMORY, 99, Ramp.LINEAR),
                    new Effect("payments-worker", AVAILABILITY, 80, Ramp.LINEAR),
                    new Effect("payments-worker", ERROR_RATE, 7, Ramp.LINEAR)));

    private final String title;
    private final String description;
    private final Duration duration;
    private final List<Effect> effects;

    ScenarioType(String title, String description, Duration duration, List<Effect> effects) {
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.effects = effects;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Duration duration() {
        return duration;
    }

    public List<Effect> effects() {
        return effects;
    }

    public List<String> affectedResources() {
        return effects.stream().map(Effect::resourceName).distinct().toList();
    }

    /** Push {@code metric} on {@code resourceName} towards {@code peak}, shaped by {@code ramp}. */
    public record Effect(String resourceName, MetricType metric, double peak, Ramp ramp) {
    }
}
