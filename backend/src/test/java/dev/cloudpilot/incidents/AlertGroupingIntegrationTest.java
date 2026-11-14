package dev.cloudpilot.incidents;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import dev.cloudpilot.alerts.AlertFired;
import dev.cloudpilot.alerts.AlertResolved;
import dev.cloudpilot.alerts.Severity;
import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** US-07 end to end: alert events in, grouped incidents out. */
class AlertGroupingIntegrationTest extends IntegrationTest {

    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CATALOG_API = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ORDERS_DB = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID RULE = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private final Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private IncidentAlertRepository incidentAlerts;

    @Autowired
    private TimelineEntryRepository timeline;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from incidents");
    }

    private AlertFired fired(UUID resource, Severity severity, String message, Instant at) {
        return new AlertFired(UUID.randomUUID(), RULE, resource, severity, message, at);
    }

    @Test
    void firstAlertOpensAnIncident() {
        events.publishEvent(fired(ORDERS_DB, Severity.WARNING, "orders-db cpu 97% > 90% for 3 min", t0));

        Incident incident = incidents.findAll().getFirst();
        assertThat(incident.getTitle()).isEqualTo("orders-db cpu 97% > 90% for 3 min");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(incident.getNumber()).isPositive();
        assertThat(incidentAlerts.findByIncidentIdOrderByFiredAtAsc(incident.getId()))
                .singleElement().satisfies(a -> assertThat(a.getReason()).isEqualTo("First alert"));
        assertThat(timeline.findByIncidentIdOrderByOccurredAtAsc(incident.getId()))
                .extracting(TimelineEntry::getType).containsExactly(TimelineEntry.Type.ALERT_FIRED);
    }

    @Test
    void relatedAlertsJoinTheSameIncidentAndEscalateIt() {
        events.publishEvent(fired(ORDERS_DB, Severity.WARNING, "orders-db cpu high", t0));
        events.publishEvent(fired(CHECKOUT_API, Severity.CRITICAL, "checkout-api errors high", t0.plusSeconds(60)));

        assertThat(incidents.findAll()).singleElement().satisfies(incident -> {
            assertThat(incident.getSeverity()).isEqualTo(Severity.CRITICAL);
            List<IncidentAlert> grouped = incidentAlerts.findByIncidentIdOrderByFiredAtAsc(incident.getId());
            assertThat(grouped).hasSize(2);
            assertThat(grouped.get(1).getReason())
                    .isEqualTo("checkout-api depends on orders-db, already in " + Incident.displayNumber(incident.getNumber()));
        });
    }

    @Test
    void unrelatedAlertsOpenSeparateIncidents() {
        events.publishEvent(fired(ORDERS_DB, Severity.WARNING, "orders-db cpu high", t0));
        events.publishEvent(fired(CATALOG_API, Severity.WARNING, "catalog-api latency high", t0.plusSeconds(30)));

        assertThat(incidents.findAll()).hasSize(2);
    }

    @Test
    void resolvedAlertsAreRecordedButTheIncidentStaysOpen() {
        AlertFired alert = fired(ORDERS_DB, Severity.WARNING, "orders-db cpu high", t0);
        events.publishEvent(alert);

        events.publishEvent(new AlertResolved(alert.alertId(), RULE, ORDERS_DB, t0.plusSeconds(600)));

        Incident incident = incidents.findAll().getFirst();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incidentAlerts.findByAlertId(alert.alertId()))
                .hasValueSatisfying(a -> assertThat(a.getResolvedAt()).isEqualTo(t0.plusSeconds(600)));
        assertThat(timeline.findByIncidentIdOrderByOccurredAtAsc(incident.getId()))
                .extracting(TimelineEntry::getType)
                .containsExactly(TimelineEntry.Type.ALERT_FIRED, TimelineEntry.Type.ALERT_RESOLVED);
    }

    @Test
    void handlingTheSameAlertTwiceIsHarmless() {
        AlertFired alert = fired(ORDERS_DB, Severity.WARNING, "orders-db cpu high", t0);

        events.publishEvent(alert);
        events.publishEvent(alert);

        assertThat(incidents.findAll()).hasSize(1);
        assertThat(incidentAlerts.findAll()).hasSize(1);
    }
}
