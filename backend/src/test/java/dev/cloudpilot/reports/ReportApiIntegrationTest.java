package dev.cloudpilot.reports;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudpilot.alerts.AlertFired;
import dev.cloudpilot.alerts.Severity;
import dev.cloudpilot.auth.Role;
import dev.cloudpilot.incidents.IncidentRepository;
import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** US-13 through the API, from real incidents. */
class ReportApiIntegrationTest extends IntegrationTest {

    private static final UUID ORDERS_DB = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CATALOG_API = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID RULE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private JdbcTemplate jdbc;

    // Before as well as after: an open incident left by another test class would otherwise absorb
    // this test's alert (the grouper attaches it) and the MTTR would be seconds, not 30 minutes.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from incidents");
    }

    private ResponseEntity<Map<String, Object>> call(HttpMethod method, String path, Object body, Role role) {
        return rest.exchange(path, method, new HttpEntity<>(body, authHeaders(role)), MAP);
    }

    @Test
    void reportsOnIncidentsInTheWindow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        events.publishEvent(new AlertFired(UUID.randomUUID(), RULE, ORDERS_DB, Severity.CRITICAL,
                "orders-db cpu 97% > 90% for 3 min", now.minus(30, ChronoUnit.MINUTES)));
        String id = incidents.findAll().getFirst().getId().toString();
        call(HttpMethod.POST, "/api/v1/incidents/" + id + "/acknowledge", null, Role.SRE);
        call(HttpMethod.POST, "/api/v1/incidents/" + id + "/investigate", null, Role.SRE);
        call(HttpMethod.POST, "/api/v1/incidents/" + id + "/resolve", Map.of("resolution", "Restarted clients"), Role.SRE);
        // A second, unrelated and still open incident (catalog-api doesn't depend on orders-db).
        events.publishEvent(new AlertFired(UUID.randomUUID(), RULE, CATALOG_API, Severity.WARNING,
                "catalog-api latency p95 1400 ms > 1000 ms for 3 min", now.minus(5, ChronoUnit.MINUTES)));

        ResponseEntity<Map<String, Object>> response = call(HttpMethod.GET, "/api/v1/reports/summary?days=7", null, Role.DEVELOPER);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> report = response.getBody();
        assertThat(report).containsEntry("opened", 2).containsEntry("resolved", 1).containsEntry("active", 1);
        assertThat(((Number) report.get("mttrMinutes")).doubleValue()).isGreaterThan(29);
        assertThat((Map<String, Object>) report.get("bySeverity")).containsEntry("CRITICAL", 1).containsEntry("WARNING", 1);
        assertThat((List<Map<String, Object>>) report.get("responders")).singleElement()
                .satisfies(r -> assertThat(r).containsEntry("name", "IT SRE").containsEntry("resolved", 1));
        assertThat((List<Map<String, Object>>) report.get("topResources")).extracting(r -> r.get("resource"))
                .contains("orders-db", "catalog-api");
    }

    @Test
    void theWindowIsValidated() {
        assertThat(call(HttpMethod.GET, "/api/v1/reports/summary?days=0", null, Role.SRE).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.GET, "/api/v1/reports/summary?days=366", null, Role.SRE).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
