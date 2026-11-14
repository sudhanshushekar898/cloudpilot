package dev.cloudpilot.simulator;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import dev.cloudpilot.metrics.LogEvent;
import dev.cloudpilot.metrics.MetricType;

import static dev.cloudpilot.metrics.LogEvent.Level.ERROR;
import static dev.cloudpilot.metrics.LogEvent.Level.WARN;
import static dev.cloudpilot.metrics.MetricType.AVAILABILITY;
import static dev.cloudpilot.metrics.MetricType.CPU;
import static dev.cloudpilot.metrics.MetricType.ERROR_RATE;
import static dev.cloudpilot.metrics.MetricType.LATENCY_P95;
import static dev.cloudpilot.metrics.MetricType.MEMORY;

/**
 * Failure scenarios an admin can start for a demo. Each one pushes specific metrics on
 * specific resources towards a peak value, following the dependency graph: the root cause
 * is hit hardest and the services that depend on it show knock-on symptoms. Scenarios also
 * write log lines and may record a deployment, which is the evidence alert grouping (M5)
 * and root-cause analysis (M7) have to work with.
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
                    new Effect("web-frontend", ERROR_RATE, 6, Ramp.FAST)),
            List.of(
                    new LogTemplate("orders-db", WARN, "too many clients already: max_connections (100) reached"),
                    new LogTemplate("orders-db", ERROR, "FATAL: remaining connection slots are reserved for non-replication superuser connections"),
                    new LogTemplate("checkout-api", ERROR, "HikariPool-1 - Connection is not available, request timed out after 30000ms"),
                    new LogTemplate("checkout-api", ERROR, "POST /api/orders failed: 503 upstream timeout (orders-db)"),
                    new LogTemplate("payments-worker", ERROR, "Failed to update payment status: could not acquire connection to orders-db"),
                    new LogTemplate("web-frontend", WARN, "Checkout request to checkout-api took 3.1 s")),
            // A harmless release shortly before: a red herring the analysis must not blame.
            Optional.of(new DeploymentSpec("catalog-api", "v3.2.1", "Update product image CDN host",
                    Duration.ofMinutes(4)))),

    BAD_DEPLOYMENT(
            "Bad checkout-api deployment",
            "A new checkout-api release throws errors on most requests.",
            Duration.ofMinutes(10),
            List.of(
                    new Effect("checkout-api", ERROR_RATE, 38, Ramp.FAST),
                    new Effect("checkout-api", AVAILABILITY, 70, Ramp.FAST),
                    new Effect("checkout-api", CPU, 75, Ramp.FAST),
                    new Effect("web-frontend", ERROR_RATE, 9, Ramp.FAST)),
            List.of(
                    new LogTemplate("checkout-api", ERROR, "java.lang.NullPointerException: Cannot invoke \"PaymentIntent.getId()\" at PaymentClient.createIntent(PaymentClient.java:88)"),
                    new LogTemplate("checkout-api", ERROR, "POST /api/checkout 500 Internal Server Error"),
                    new LogTemplate("web-frontend", WARN, "checkout-api returned 500 for POST /checkout")),
            Optional.of(new DeploymentSpec("checkout-api", "v2.14.0", "Switch to new payment provider SDK",
                    Duration.ZERO))),

    QUEUE_BACKLOG(
            "Payments queue backlog",
            "payments-worker can't keep up; messages pile up in payments-queue.",
            Duration.ofMinutes(15),
            List.of(
                    new Effect("payments-queue", LATENCY_P95, 9000, Ramp.LINEAR),
                    new Effect("payments-worker", CPU, 96, Ramp.FAST),
                    new Effect("checkout-api", LATENCY_P95, 1400, Ramp.LINEAR)),
            List.of(
                    new LogTemplate("payments-queue", WARN, "ApproximateAgeOfOldestMessage above 5 min, 42000 messages visible"),
                    new LogTemplate("payments-worker", WARN, "Consumer lag growing: processing 120 msg/s, receiving 480 msg/s"),
                    new LogTemplate("checkout-api", WARN, "Waiting for payment confirmation exceeded 1 s")),
            Optional.empty()),

    POD_MEMORY_LEAK(
            "Memory leak in payments-worker",
            "payments-worker memory grows until pods are OOM-killed and restart.",
            Duration.ofMinutes(20),
            List.of(
                    new Effect("payments-worker", MEMORY, 99, Ramp.LINEAR),
                    new Effect("payments-worker", AVAILABILITY, 80, Ramp.LINEAR),
                    new Effect("payments-worker", ERROR_RATE, 7, Ramp.LINEAR)),
            List.of(
                    new LogTemplate("payments-worker", WARN, "GC overhead above 40%, heap 1.7 GiB of 2 GiB used"),
                    new LogTemplate("payments-worker", ERROR, "Container payments-worker-7c9f terminated: OOMKilled (exit code 137)")),
            Optional.of(new DeploymentSpec("payments-worker", "v1.8.2", "Cache payment receipts in memory",
                    Duration.ofMinutes(2))));

    private final String title;
    private final String description;
    private final Duration duration;
    private final List<Effect> effects;
    private final List<LogTemplate> logs;
    private final Optional<DeploymentSpec> deployment;

    ScenarioType(String title, String description, Duration duration, List<Effect> effects,
                 List<LogTemplate> logs, Optional<DeploymentSpec> deployment) {
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.effects = effects;
        this.logs = logs;
        this.deployment = deployment;
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

    public List<LogTemplate> logs() {
        return logs;
    }

    public Optional<DeploymentSpec> deployment() {
        return deployment;
    }

    public List<String> affectedResources() {
        return effects.stream().map(Effect::resourceName).distinct().toList();
    }

    /** Push {@code metric} on {@code resourceName} towards {@code peak}, shaped by {@code ramp}. */
    public record Effect(String resourceName, MetricType metric, double peak, Ramp ramp) {
    }

    /** A log line the resource writes while the scenario affects it. */
    public record LogTemplate(String resourceName, LogEvent.Level level, String message) {
    }

    /** A release recorded {@code before} the scenario starts. */
    public record DeploymentSpec(String resourceName, String version, String description, Duration before) {
    }
}
