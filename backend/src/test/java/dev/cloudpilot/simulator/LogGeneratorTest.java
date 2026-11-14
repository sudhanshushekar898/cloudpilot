package dev.cloudpilot.simulator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import dev.cloudpilot.metrics.LogEvent;
import dev.cloudpilot.resources.ResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogGeneratorTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");
    private static final UUID CHECKOUT = UUID.randomUUID();
    private static final UUID FRONTEND = UUID.randomUUID();
    private static final UUID CATALOG = UUID.randomUUID();

    private static final List<SimulatedResource> TOPOLOGY = List.of(
            new SimulatedResource(CHECKOUT, "checkout-api", ResourceType.API),
            new SimulatedResource(FRONTEND, "web-frontend", ResourceType.EC2),
            new SimulatedResource(CATALOG, "catalog-api", ResourceType.API));

    private final LogGenerator generator = new LogGenerator(new Random(7));

    private static ActiveScenario running(ScenarioType type) {
        return new ActiveScenario(type, T0, T0.plus(type.duration()), "test");
    }

    @Test
    void writesNothingWithoutAScenario() {
        assertThat(generator.generate(TOPOLOGY, T0, Optional.empty())).isEmpty();
    }

    @Test
    void writesNothingBeforeTheEffectIsNoticeable() {
        // At 5% progress a FAST ramp is only at 25% strength: too early for errors in the logs.
        Instant early = T0.plus(Duration.ofSeconds(30));

        assertThat(generator.generate(TOPOLOGY, early, Optional.of(running(ScenarioType.BAD_DEPLOYMENT)))).isEmpty();
    }

    @Test
    void writesOneLineFromTheScenarioTemplatesPerAffectedResource() {
        Instant midway = T0.plus(Duration.ofMinutes(5));

        List<LogEvent> logs = generator.generate(TOPOLOGY, midway, Optional.of(running(ScenarioType.BAD_DEPLOYMENT)));

        assertThat(logs).extracting(LogEvent::resourceId).containsExactlyInAnyOrder(CHECKOUT, FRONTEND);
        assertThat(logs).allSatisfy(log -> assertThat(log.loggedAt()).isEqualTo(midway));
        List<String> templates = ScenarioType.BAD_DEPLOYMENT.logs().stream().map(ScenarioType.LogTemplate::message).toList();
        assertThat(logs).extracting(LogEvent::message).allSatisfy(message -> assertThat(templates).contains(message));
    }
}
