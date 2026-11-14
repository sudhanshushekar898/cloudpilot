package dev.cloudpilot.incidents;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** US-07: alerts on resources that depend on each other, within 10 minutes, join one incident. */
class AlertGrouperTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    private static final UUID FRONTEND = UUID.randomUUID();
    private static final UUID CHECKOUT = UUID.randomUUID();
    private static final UUID CATALOG = UUID.randomUUID();
    private static final UUID ORDERS_DB = UUID.randomUUID();

    // web-frontend -> checkout-api -> orders-db ; web-frontend -> catalog-api
    private static final Map<UUID, Set<UUID>> DEPENDS_ON = Map.of(
            FRONTEND, Set.of(CHECKOUT, CATALOG),
            CHECKOUT, Set.of(ORDERS_DB),
            CATALOG, Set.of(),
            ORDERS_DB, Set.of());

    private static final Map<UUID, String> NAMES = Map.of(
            FRONTEND, "web-frontend", CHECKOUT, "checkout-api", CATALOG, "catalog-api", ORDERS_DB, "orders-db");

    private final AlertGrouper grouper = new AlertGrouper(Duration.ofMinutes(10));

    private static AlertGrouper.Candidate incident(long number, Instant lastAlertAt, UUID... resources) {
        return new AlertGrouper.Candidate(UUID.randomUUID(), number, Set.of(resources), lastAlertAt);
    }

    @Test
    void joinsAnIncidentOnAResourceItDependsOn() {
        AlertGrouper.Candidate dbIncident = incident(7, T0, ORDERS_DB);

        var match = grouper.match(CHECKOUT, T0.plusSeconds(90), List.of(dbIncident), DEPENDS_ON, NAMES);

        assertThat(match).hasValueSatisfying(m -> {
            assertThat(m.incidentId()).isEqualTo(dbIncident.incidentId());
            assertThat(m.reason()).isEqualTo("checkout-api depends on orders-db, already in INC-7");
        });
    }

    @Test
    void joinsAnIncidentOnAResourceThatDependsOnIt() {
        AlertGrouper.Candidate checkoutIncident = incident(3, T0, CHECKOUT);

        var match = grouper.match(ORDERS_DB, T0.plusSeconds(30), List.of(checkoutIncident), DEPENDS_ON, NAMES);

        assertThat(match).hasValueSatisfying(m ->
                assertThat(m.reason()).isEqualTo("checkout-api (in INC-3) depends on orders-db"));
    }

    @Test
    void joinsAnIncidentAlreadyCoveringTheSameResource() {
        AlertGrouper.Candidate checkoutIncident = incident(3, T0, CHECKOUT);

        var match = grouper.match(CHECKOUT, T0.plusSeconds(30), List.of(checkoutIncident), DEPENDS_ON, NAMES);

        assertThat(match).hasValueSatisfying(m ->
                assertThat(m.reason()).isEqualTo("checkout-api already has alerts in INC-3"));
    }

    @Test
    void chainsThroughResourcesAlreadyInTheIncident() {
        // DB and checkout are in the incident; the frontend depends on checkout, so it joins too.
        AlertGrouper.Candidate incident = incident(5, T0, ORDERS_DB, CHECKOUT);

        assertThat(grouper.match(FRONTEND, T0.plusSeconds(60), List.of(incident), DEPENDS_ON, NAMES)).isPresent();
    }

    @Test
    void doesNotGroupUnrelatedResources() {
        AlertGrouper.Candidate dbIncident = incident(7, T0, ORDERS_DB);

        assertThat(grouper.match(CATALOG, T0.plusSeconds(30), List.of(dbIncident), DEPENDS_ON, NAMES)).isEmpty();
    }

    @Test
    void doesNotGroupWhenTheIncidentWentQuietMoreThanTenMinutesAgo() {
        AlertGrouper.Candidate dbIncident = incident(7, T0, ORDERS_DB);

        assertThat(grouper.match(CHECKOUT, T0.plus(Duration.ofMinutes(11)), List.of(dbIncident), DEPENDS_ON, NAMES))
                .isEmpty();
    }

    @Test
    void prefersTheMostRecentlyActiveIncident() {
        AlertGrouper.Candidate older = incident(1, T0, ORDERS_DB);
        AlertGrouper.Candidate newer = incident(2, T0.plusSeconds(120), ORDERS_DB);

        var match = grouper.match(CHECKOUT, T0.plusSeconds(180), List.of(older, newer), DEPENDS_ON, NAMES);

        assertThat(match).hasValueSatisfying(m -> assertThat(m.incidentId()).isEqualTo(newer.incidentId()));
    }
}
