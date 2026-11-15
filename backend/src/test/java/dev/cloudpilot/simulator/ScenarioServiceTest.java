package dev.cloudpilot.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import dev.cloudpilot.features.FeatureFlags;
import dev.cloudpilot.metrics.Deployment;
import dev.cloudpilot.metrics.EventStore;
import dev.cloudpilot.resources.ResourceRef;
import dev.cloudpilot.resources.ResourceService;
import dev.cloudpilot.resources.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Mock
    private ResourceService resources;

    @Mock
    private EventStore events;

    @Mock
    private FeatureFlags flags;

    private ScenarioManager manager;
    private ScenarioService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        manager = new ScenarioManager(clock);
        lenient().when(flags.isEnabled(FeatureFlags.FAILURE_SCENARIOS)).thenReturn(true);
        service = new ScenarioService(manager, resources, events, flags);
    }

    @Test
    void startingABadDeploymentRecordsTheRelease() {
        UUID checkout = UUID.randomUUID();
        when(resources.topology()).thenReturn(java.util.Map.of(
                checkout, new ResourceRef(checkout, "checkout-api", ResourceType.API, java.util.Set.of())));

        ActiveScenario started = service.start(ScenarioType.BAD_DEPLOYMENT, "admin@cloudpilot.dev");

        assertThat(manager.active()).contains(started);
        ArgumentCaptor<Deployment> saved = ArgumentCaptor.forClass(Deployment.class);
        verify(events).saveDeployment(saved.capture());
        assertThat(saved.getValue().resourceId()).isEqualTo(checkout);
        assertThat(saved.getValue().version()).isEqualTo("v2.14.0");
        assertThat(saved.getValue().deployedAt()).isEqualTo(NOW);
    }

    @Test
    void deploymentIsBackdatedByTheScenarioOffset() {
        UUID worker = UUID.randomUUID();
        when(resources.topology()).thenReturn(java.util.Map.of(
                worker, new ResourceRef(worker, "payments-worker", ResourceType.POD, java.util.Set.of())));

        service.start(ScenarioType.POD_MEMORY_LEAK, "admin");

        ArgumentCaptor<Deployment> saved = ArgumentCaptor.forClass(Deployment.class);
        verify(events).saveDeployment(saved.capture());
        assertThat(saved.getValue().deployedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(2)));
    }

    @Test
    void scenariosWithoutADeploymentRecordNone() {
        service.start(ScenarioType.QUEUE_BACKLOG, "admin");

        verify(events, never()).saveDeployment(any());
    }
}
