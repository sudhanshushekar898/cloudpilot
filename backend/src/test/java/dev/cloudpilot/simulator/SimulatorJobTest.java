package dev.cloudpilot.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import dev.cloudpilot.metrics.EventStore;
import dev.cloudpilot.metrics.LogEvent;
import dev.cloudpilot.metrics.MetricSample;
import dev.cloudpilot.metrics.MetricStore;
import dev.cloudpilot.metrics.MetricType;
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
class SimulatorJobTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Mock
    private CloudResourceRepository resources;

    @Mock
    private MetricStore store;

    @Mock
    private EventStore events;

    private ScenarioManager scenarios;
    private SimulatorJob job;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        scenarios = new ScenarioManager(clock);
        job = new SimulatorJob(resources, store, events, new MetricGenerator(new Random(1)),
                new LogGenerator(new Random(1)), scenarios,
                new SimulatorProperties(true, Duration.ofSeconds(10), Duration.ofHours(1)), clock);
    }

    private void givenTwoResources() {
        when(resources.findAll()).thenReturn(List.of(
                new CloudResource(UUID.randomUUID(), "orders-db", ResourceType.RDS, "ap-south-1", "db"),
                new CloudResource(UUID.randomUUID(), "checkout-api", ResourceType.API, "ap-south-1", "api")));
    }

    @SuppressWarnings("unchecked")
    private Collection<MetricSample> savedBatch() {
        ArgumentCaptor<Collection<MetricSample>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(store).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void tickWritesOneSamplePerMetricPerResource() {
        givenTwoResources();

        job.tick();

        Collection<MetricSample> batch = savedBatch();
        assertThat(batch).hasSize(2 * MetricType.values().length);
        assertThat(batch).allSatisfy(s -> assertThat(s.recordedAt()).isEqualTo(NOW));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickWritesScenarioLogsWhileAFailureIsRunning() {
        givenTwoResources();
        // Started at NOW; the job below runs 5 minutes later, when the DB scenario is at full strength.
        scenarios.start(ScenarioType.DB_CONNECTION_EXHAUSTION, "test");
        Clock later = Clock.fixed(NOW.plus(Duration.ofMinutes(5)), ZoneOffset.UTC);
        SimulatorJob laterJob = new SimulatorJob(resources, store, events, new MetricGenerator(new Random(1)),
                new LogGenerator(new Random(1)), scenarios,
                new SimulatorProperties(true, Duration.ofSeconds(10), Duration.ofHours(1)), later);

        laterJob.tick();

        ArgumentCaptor<Collection<LogEvent>> logs = ArgumentCaptor.forClass(Collection.class);
        verify(events).saveLogs(logs.capture());
        assertThat(logs.getValue()).hasSize(2);   // orders-db and checkout-api both have log templates
    }

    @Test
    void backfillCreatesAnHourOfHistoryWhenEmpty() {
        givenTwoResources();
        when(store.hasSamplesSince(NOW.minus(Duration.ofHours(1)))).thenReturn(false);

        job.backfillIfEmpty();

        // 1 hour at 30-second steps = 120 points, for 2 resources x 5 metrics.
        Collection<MetricSample> batch = savedBatch();
        assertThat(batch).hasSize(120 * 2 * MetricType.values().length);
        assertThat(batch).allSatisfy(s -> assertThat(s.recordedAt()).isBefore(NOW));
    }

    @Test
    void backfillDoesNothingWhenRecentDataExists() {
        when(store.hasSamplesSince(any())).thenReturn(true);

        job.backfillIfEmpty();

        verify(store, never()).saveAll(any());
    }
}
