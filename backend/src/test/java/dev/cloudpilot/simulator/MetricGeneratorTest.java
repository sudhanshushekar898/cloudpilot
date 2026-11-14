package dev.cloudpilot.simulator;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.cloudpilot.metrics.MetricSample;
import dev.cloudpilot.metrics.MetricType;
import dev.cloudpilot.resources.HealthEvaluator;
import dev.cloudpilot.resources.ResourceStatus;
import dev.cloudpilot.resources.ResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricGeneratorTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    private static final List<SimulatedResource> TOPOLOGY = List.of(
            new SimulatedResource(UUID.randomUUID(), "web-frontend", ResourceType.EC2),
            new SimulatedResource(UUID.randomUUID(), "checkout-api", ResourceType.API),
            new SimulatedResource(UUID.randomUUID(), "catalog-api", ResourceType.API),
            new SimulatedResource(UUID.randomUUID(), "orders-db", ResourceType.RDS),
            new SimulatedResource(UUID.randomUUID(), "payments-queue", ResourceType.QUEUE),
            new SimulatedResource(UUID.randomUUID(), "payments-worker", ResourceType.POD),
            new SimulatedResource(UUID.randomUUID(), "inventory-pod", ResourceType.POD));

    // Fixed seed: random-looking data, but the same every run, so the test is not flaky.
    private final MetricGenerator generator = new MetricGenerator(new Random(42));
    private final HealthEvaluator health = new HealthEvaluator();

    private Map<String, ResourceStatus> statusAt(Instant at, Optional<ActiveScenario> scenario) {
        List<MetricSample> samples = generator.generate(TOPOLOGY, at, scenario);
        Map<UUID, Map<MetricType, Double>> byResource = samples.stream().collect(Collectors.groupingBy(
                MetricSample::resourceId,
                Collectors.toMap(MetricSample::metric, MetricSample::value, (a, b) -> b,
                        () -> new EnumMap<>(MetricType.class))));
        return TOPOLOGY.stream().collect(Collectors.toMap(
                SimulatedResource::name, r -> health.evaluate(byResource.get(r.id()))));
    }

    private static ActiveScenario running(ScenarioType type) {
        return new ActiveScenario(type, T0, T0.plus(type.duration()), "test");
    }

    @Test
    void producesEveryMetricForEveryResource() {
        List<MetricSample> samples = generator.generate(TOPOLOGY, T0, Optional.empty());

        assertThat(samples).hasSize(TOPOLOGY.size() * MetricType.values().length);
        assertThat(samples).allSatisfy(s -> assertThat(s.recordedAt()).isEqualTo(T0));
    }

    @Test
    void normalTrafficStaysHealthyAcrossManyTicks() {
        for (int tick = 0; tick < 500; tick++) {
            Map<String, ResourceStatus> statuses = statusAt(T0.plusSeconds(tick * 10L), Optional.empty());
            assertThat(statuses.values()).as("tick %d: %s", tick, statuses).containsOnly(ResourceStatus.HEALTHY);
        }
    }

    @Test
    void percentagesStayWithinZeroAndHundred() {
        for (int tick = 0; tick < 200; tick++) {
            generator.generate(TOPOLOGY, T0, Optional.of(running(ScenarioType.POD_MEMORY_LEAK))).stream()
                    .filter(s -> s.metric() != MetricType.LATENCY_P95)
                    .forEach(s -> assertThat(s.value()).isBetween(0.0, 100.0));
        }
    }

    @Test
    void dbExhaustionHitsTheDatabaseAndItsDependentsButNotUnrelatedServices() {
        ActiveScenario scenario = running(ScenarioType.DB_CONNECTION_EXHAUSTION);
        Instant midway = T0.plus(Duration.ofMinutes(5));

        Map<String, ResourceStatus> statuses = statusAt(midway, Optional.of(scenario));

        assertThat(statuses.get("orders-db")).isEqualTo(ResourceStatus.DEGRADED);
        assertThat(statuses.get("checkout-api")).isEqualTo(ResourceStatus.DEGRADED);
        assertThat(statuses.get("catalog-api")).isEqualTo(ResourceStatus.HEALTHY);
        assertThat(statuses.get("inventory-pod")).isEqualTo(ResourceStatus.HEALTHY);
    }

    @Test
    void badDeploymentTakesCheckoutDown() {
        ActiveScenario scenario = running(ScenarioType.BAD_DEPLOYMENT);

        Map<String, ResourceStatus> statuses = statusAt(T0.plus(Duration.ofMinutes(3)), Optional.of(scenario));

        assertThat(statuses.get("checkout-api")).isEqualTo(ResourceStatus.DOWN);
    }

    @Test
    void scenarioEffectRampsUpFromNormal() {
        ActiveScenario scenario = running(ScenarioType.POD_MEMORY_LEAK);

        assertThat(statusAt(T0, Optional.of(scenario)).get("payments-worker")).isEqualTo(ResourceStatus.HEALTHY);
        assertThat(statusAt(T0.plus(Duration.ofMinutes(19)), Optional.of(scenario)).get("payments-worker"))
                .isEqualTo(ResourceStatus.DOWN);
    }
}
