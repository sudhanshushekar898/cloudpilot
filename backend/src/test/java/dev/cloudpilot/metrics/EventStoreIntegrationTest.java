package dev.cloudpilot.metrics;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class EventStoreIntegrationTest extends IntegrationTest {

    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ORDERS_DB = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CATALOG_API = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    @Autowired
    private EventStore events;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from log_events");
        jdbc.update("delete from deployments");
    }

    @Test
    void returnsLogsForTheGivenResourcesAndTimeRangeNewestFirst() {
        events.saveLogs(List.of(
                new LogEvent(CHECKOUT_API, LogEvent.Level.ERROR, "timeout calling orders-db", T0.plusSeconds(10)),
                new LogEvent(ORDERS_DB, LogEvent.Level.WARN, "connection pool 100% used", T0.plusSeconds(20)),
                new LogEvent(CATALOG_API, LogEvent.Level.ERROR, "other service", T0.plusSeconds(30)),
                new LogEvent(CHECKOUT_API, LogEvent.Level.ERROR, "too early", T0.minusSeconds(600))));

        List<LogEvent> logs = events.logs(Set.of(CHECKOUT_API, ORDERS_DB), T0, T0.plusSeconds(60), 100);

        assertThat(logs).extracting(LogEvent::message)
                .containsExactly("connection pool 100% used", "timeout calling orders-db");
    }

    @Test
    void limitsTheNumberOfLogLines() {
        events.saveLogs(List.of(
                new LogEvent(CHECKOUT_API, LogEvent.Level.ERROR, "one", T0.plusSeconds(1)),
                new LogEvent(CHECKOUT_API, LogEvent.Level.ERROR, "two", T0.plusSeconds(2)),
                new LogEvent(CHECKOUT_API, LogEvent.Level.ERROR, "three", T0.plusSeconds(3))));

        assertThat(events.logs(Set.of(CHECKOUT_API), T0, T0.plusSeconds(60), 2))
                .extracting(LogEvent::message).containsExactly("three", "two");
    }

    @Test
    void returnsDeploymentsForTheGivenResourcesAndTimeRange() {
        Deployment deploy = new Deployment(UUID.randomUUID(), CHECKOUT_API, "v2.14.0", "New payment flow",
                "ci-pipeline", T0.plusSeconds(5));
        events.saveDeployment(deploy);
        events.saveDeployment(new Deployment(UUID.randomUUID(), CATALOG_API, "v1.0.1", "other", "ci", T0));

        assertThat(events.deployments(Set.of(CHECKOUT_API, ORDERS_DB), T0, T0.plusSeconds(60)))
                .containsExactly(deploy);
    }

    @Test
    void emptyResourceSetReturnsNothing() {
        assertThat(events.logs(Set.of(), T0, T0.plusSeconds(60), 10)).isEmpty();
        assertThat(events.deployments(Set.of(), T0, T0.plusSeconds(60))).isEmpty();
    }
}
