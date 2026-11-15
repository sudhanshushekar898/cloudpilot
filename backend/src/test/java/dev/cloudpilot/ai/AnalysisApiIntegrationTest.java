package dev.cloudpilot.ai;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudpilot.alerts.AlertFired;
import dev.cloudpilot.alerts.Severity;
import dev.cloudpilot.auth.Role;
import dev.cloudpilot.incidents.IncidentRepository;
import dev.cloudpilot.metrics.Deployment;
import dev.cloudpilot.metrics.EventStore;
import dev.cloudpilot.metrics.LogEvent;
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

/**
 * US-11 end to end with no API key: the rule-based analyser answers, grounded in the
 * incident's real alerts, logs, deployments and runbooks.
 */
class AnalysisApiIntegrationTest extends IntegrationTest {

    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CATALOG_API = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ORDERS_DB = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID RULE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final Instant t0 = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID incidentId;

    @BeforeEach
    void dbExhaustionIncident() {
        // The red herring: an unrelated catalog-api release 4 minutes before the failure.
        eventStore.saveDeployment(new Deployment(UUID.randomUUID(), CATALOG_API, "v3.2.1", "Update product image CDN host",
                "ci-pipeline", t0.minusSeconds(240)));
        events.publishEvent(new AlertFired(UUID.randomUUID(), RULE, ORDERS_DB, Severity.CRITICAL,
                "orders-db cpu 97% > 90% for 3 min", t0));
        events.publishEvent(new AlertFired(UUID.randomUUID(), RULE, CHECKOUT_API, Severity.CRITICAL,
                "checkout-api error rate 18% > 5% for 5 min", t0.plusSeconds(60)));
        eventStore.saveLogs(List.of(
                new LogEvent(ORDERS_DB, LogEvent.Level.WARN, "too many clients already: max_connections (100) reached", t0.plusSeconds(10)),
                new LogEvent(CHECKOUT_API, LogEvent.Level.ERROR, "HikariPool-1 - Connection is not available, request timed out after 30000ms", t0.plusSeconds(20))));
        incidentId = incidents.findAll().getFirst().getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from incidents");
        jdbc.update("delete from log_events");
        jdbc.update("delete from deployments");
    }

    private ResponseEntity<Map<String, Object>> analyse(Role role, String question) {
        return rest.exchange("/api/v1/incidents/" + incidentId + "/analysis", HttpMethod.POST,
                new HttpEntity<>(Map.of("question", question), authHeaders(role)), MAP);
    }

    @Test
    void anyTeamMemberCanAskAndGetsAnEvidenceBackedAnswer() {
        ResponseEntity<Map<String, Object>> response = analyse(Role.DEVELOPER, "Why did checkout fail?");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> analysis = response.getBody();
        assertThat(analysis).containsEntry("analyzer", "rule-based").containsEntry("question", "Why did checkout fail?")
                .containsEntry("requestedBy", "it-developer@test.dev");

        Map<String, Object> cause = (Map<String, Object>) analysis.get("likelyCause");
        assertThat((String) cause.get("text")).contains("orders-db").doesNotContain("catalog-api");
        assertThat((List<String>) cause.get("citations")).isNotEmpty();

        List<Map<String, Object>> evidence = (List<Map<String, Object>>) analysis.get("evidence");
        assertThat(evidence).extracting(e -> e.get("id")).contains("A1", "A2", "D1", "L1", "L2", "R1");
        assertThat(evidence).filteredOn(e -> "RUNBOOK".equals(e.get("kind")))
                .anySatisfy(e -> assertThat((String) e.get("source")).startsWith("Orders DB connection exhaustion"));
        assertThat((List<?>) analysis.get("verification")).isNotEmpty();
        assertThat((List<?>) analysis.get("remediation")).isNotEmpty();
    }

    @Test
    void everyCitationPointsAtEvidenceThatWasProvided() {
        Map<String, Object> analysis = analyse(Role.SRE, "Why?").getBody();

        List<String> ids = ((List<Map<String, Object>>) analysis.get("evidence")).stream()
                .map(e -> (String) e.get("id")).toList();
        List<String> cited = new java.util.ArrayList<>(((List<String>) ((Map<String, Object>) analysis.get("likelyCause")).get("citations")));
        ((List<Map<String, Object>>) analysis.get("verification")).forEach(s -> cited.addAll((List<String>) s.get("citations")));
        ((List<Map<String, Object>>) analysis.get("remediation")).forEach(s -> cited.addAll((List<String>) s.get("citations")));

        assertThat(ids).containsAll(cited);
    }

    @Test
    void theLatestAnalysisCanBeReadBack() {
        analyse(Role.SRE, "First question");

        ResponseEntity<Map<String, Object>> latest = rest.exchange("/api/v1/incidents/" + incidentId + "/analysis",
                HttpMethod.GET, new HttpEntity<>(authHeaders(Role.DEVELOPER)), MAP);

        assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(latest.getBody()).containsEntry("question", "First question");
    }

    @Test
    void noAnalysisYetIs404() {
        ResponseEntity<Map<String, Object>> latest = rest.exchange("/api/v1/incidents/" + incidentId + "/analysis",
                HttpMethod.GET, new HttpEntity<>(authHeaders(Role.DEVELOPER)), MAP);

        assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void repeatingTheSameQuestionWithinThirtySecondsReusesTheAnswer() {
        Map<String, Object> first = analyse(Role.SRE, "Same question").getBody();
        Map<String, Object> second = analyse(Role.SRE, "Same question").getBody();

        assertThat(second.get("id")).isEqualTo(first.get("id"));
        assertThat(jdbc.queryForObject("select count(*) from incident_analyses", Integer.class)).isEqualTo(1);
    }

    @Test
    void questionsAreValidated() {
        assertThat(analyse(Role.SRE, "x".repeat(501)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownIncidentIs404() {
        ResponseEntity<Map<String, Object>> response = rest.exchange("/api/v1/incidents/" + UUID.randomUUID() + "/analysis",
                HttpMethod.POST, new HttpEntity<>(Map.of("question", "Why?"), authHeaders(Role.SRE)), MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
