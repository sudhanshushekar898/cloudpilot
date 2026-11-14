package dev.cloudpilot.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import dev.cloudpilot.metrics.Deployment;
import dev.cloudpilot.metrics.EventStore;
import dev.cloudpilot.resources.CloudResource;
import dev.cloudpilot.resources.CloudResourceRepository;
import dev.cloudpilot.resources.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Mock
    private CloudResourceRepository resources;

    @Mock
    private EventStore events;

    private ScenarioManager manager;
    private ScenarioService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        manager = new ScenarioManager(clock);
        service = new ScenarioService(manager, resources, events);
    }

    @Test
    void startingABadDeploymentRecordsTheRelease() {
        UUID checkout = UUID.randomUUID();
        when(resources.findByName("checkout-api"))
                .thenReturn(Optional.of(new CloudResource(checkout, "checkout-api", ResourceType.API, "ap-south-1", "api")));

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
        when(resources.findByName("payments-worker")).thenReturn(Optional.of(
                new CloudResource(UUID.randomUUID(), "payments-worker", ResourceType.POD, "ap-south-1", "pod")));

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
