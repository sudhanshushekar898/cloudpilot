package dev.cloudpilot.incidents;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudpilot.alerts.AlertFired;
import dev.cloudpilot.alerts.Severity;
import dev.cloudpilot.auth.Role;
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

/** US-08 (lifecycle) and US-09 (timeline) through the HTTP API. */
class IncidentApiIntegrationTest extends IntegrationTest {

    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ORDERS_DB = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID RULE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};

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

    private String incidentId;

    @BeforeEach
    void openIncidentWithTwoGroupedAlerts() {
        events.publishEvent(new AlertFired(UUID.randomUUID(), RULE, ORDERS_DB, Severity.CRITICAL,
                "orders-db cpu 97% > 90% for 3 min", t0));
        events.publishEvent(new AlertFired(UUID.randomUUID(), RULE, CHECKOUT_API, Severity.CRITICAL,
                "checkout-api error rate 18% > 5% for 5 min", t0.plusSeconds(60)));
        incidentId = incidents.findAll().getFirst().getId().toString();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from incidents");
        jdbc.update("delete from log_events");
        jdbc.update("delete from deployments");
    }

    private <T> ResponseEntity<T> call(HttpMethod method, String path, Object body, Role role,
                                       ParameterizedTypeReference<T> type) {
        return rest.exchange(path, method, new HttpEntity<>(body, authHeaders(role)), type);
    }

    private ResponseEntity<Map<String, Object>> post(String action, Object body, Role role) {
        return call(HttpMethod.POST, "/api/v1/incidents/" + incidentId + "/" + action, body, role, MAP);
    }

    private Map<String, Object> detail() {
        return call(HttpMethod.GET, "/api/v1/incidents/" + incidentId, null, Role.DEVELOPER, MAP).getBody();
    }

    private List<Map<String, Object>> timeline(String query) {
        return call(HttpMethod.GET, "/api/v1/incidents/" + incidentId + "/timeline" + query, null, Role.DEVELOPER, LIST)
                .getBody();
    }

    @Test
    void listsActiveIncidentsWithAlertCounts() {
        List<Map<String, Object>> active = call(HttpMethod.GET, "/api/v1/incidents?scope=active", null,
                Role.DEVELOPER, LIST).getBody();

        assertThat(active).singleElement().satisfies(incident -> assertThat(incident)
                .containsEntry("status", "OPEN")
                .containsEntry("severity", "CRITICAL")
                .containsEntry("alertCount", 2)
                .containsEntry("activeAlertCount", 2));
        assertThat((String) active.getFirst().get("key")).startsWith("INC-");
    }

    @Test
    void detailShowsGroupedAlertsAndTheProbableOrigin() {
        Map<String, Object> detail = detail();

        List<Map<String, Object>> alerts = (List<Map<String, Object>>) detail.get("alerts");
        assertThat(alerts).extracting(a -> a.get("resourceName")).containsExactly("orders-db", "checkout-api");
        assertThat((String) alerts.get(1).get("reason")).startsWith("checkout-api depends on orders-db");

        List<Map<String, Object>> resources = (List<Map<String, Object>>) detail.get("resources");
        assertThat(resources).filteredOn(r -> Boolean.TRUE.equals(r.get("probableOrigin")))
                .extracting(r -> r.get("name")).containsExactly("orders-db");
    }

    @Test
    void sreWalksTheIncidentThroughItsLifecycle() {
        assertThat(post("acknowledge", null, Role.SRE).getBody())
                .containsEntry("status", "ACKNOWLEDGED");
        assertThat((Map<String, Object>) detail().get("assignee")).containsEntry("displayName", "IT SRE");

        assertThat(post("investigate", null, Role.SRE).getBody()).containsEntry("status", "INVESTIGATING");
        ResponseEntity<Map<String, Object>> resolved =
                post("resolve", Map.of("resolution", "Raised max_connections and restarted checkout-api"), Role.SRE);

        assertThat(resolved.getBody()).containsEntry("status", "RESOLVED")
                .containsEntry("resolution", "Raised max_connections and restarted checkout-api");
        assertThat(timeline("?type=STATUS_CHANGED")).extracting(e -> e.get("message")).containsExactly(
                "Status changed from OPEN to ACKNOWLEDGED",
                "Status changed from ACKNOWLEDGED to INVESTIGATING",
                "Status changed from INVESTIGATING to RESOLVED");
        assertThat(timeline("?type=STATUS_CHANGED")).allSatisfy(e -> assertThat(e).containsEntry("actor", "it-sre@test.dev"));
        assertThat(call(HttpMethod.GET, "/api/v1/incidents?scope=resolved", null, Role.SRE, LIST).getBody()).hasSize(1);
    }

    @Test
    void developersCannotChangeIncidentState() {
        assertThat(post("acknowledge", null, Role.DEVELOPER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void skippingAStepReturns409() {
        ResponseEntity<Map<String, Object>> response = post("investigate", null, Role.SRE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("detail", "Cannot move incident from OPEN to INVESTIGATING");
    }

    @Test
    void resolvingWithoutANoteReturns400() {
        post("acknowledge", null, Role.SRE);
        post("investigate", null, Role.SRE);

        assertThat(post("resolve", Map.of("resolution", ""), Role.SRE).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sreCanAssignToAnotherResponder() {
        authHeaders(Role.ADMIN);   // make sure the admin user exists
        List<Map<String, Object>> people = call(HttpMethod.GET, "/api/v1/users/assignable", null, Role.SRE, LIST).getBody();
        Map<String, Object> admin = people.stream().filter(p -> "IT ADMIN".equals(p.get("displayName"))).findFirst().orElseThrow();

        assertThat(post("assign", Map.of("userId", admin.get("id")), Role.SRE).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat((Map<String, Object>) detail().get("assignee")).containsEntry("displayName", "IT ADMIN");
        assertThat(timeline("?type=ASSIGNED")).singleElement()
                .satisfies(e -> assertThat(e).containsEntry("message", "Assigned to IT ADMIN"));
    }

    @Test
    void anyoneCanComment() {
        assertThat(post("comments", Map.of("text", "Seeing timeouts from orders-db in our logs too"), Role.DEVELOPER)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(timeline("?type=COMMENT")).singleElement().satisfies(e -> assertThat(e)
                .containsEntry("message", "Seeing timeouts from orders-db in our logs too")
                .containsEntry("actor", "it-developer@test.dev"));
    }

    @Test
    void timelineMergesAlertsLogsAndDeploymentsInTimeOrder() {
        eventStore.saveDeployment(new Deployment(UUID.randomUUID(), CHECKOUT_API, "v2.14.0", "New payment SDK",
                "ci-pipeline", t0.minusSeconds(120)));
        eventStore.saveLogs(List.of(new LogEvent(ORDERS_DB, LogEvent.Level.ERROR, "too many clients already",
                t0.plusSeconds(30))));

        List<Map<String, Object>> all = timeline("");

        assertThat(all).extracting(e -> e.get("type")).containsExactly("DEPLOYMENT", "ALERT_FIRED", "LOG", "ALERT_FIRED");
        assertThat(all.getFirst()).containsEntry("resourceName", "checkout-api")
                .containsEntry("message", "Deployed v2.14.0: New payment SDK");
        assertThat(all.get(2)).containsEntry("level", "ERROR");
    }

    @Test
    void timelineCanBeFilteredByTypeAndResource() {
        eventStore.saveLogs(List.of(
                new LogEvent(ORDERS_DB, LogEvent.Level.ERROR, "db log", t0.plusSeconds(30)),
                new LogEvent(CHECKOUT_API, LogEvent.Level.ERROR, "api log", t0.plusSeconds(40))));

        assertThat(timeline("?type=LOG")).extracting(e -> e.get("message")).containsExactly("db log", "api log");
        assertThat(timeline("?type=LOG&resourceId=" + CHECKOUT_API)).extracting(e -> e.get("message"))
                .containsExactly("api log");
    }

    @Test
    void unknownIncidentIs404() {
        assertThat(call(HttpMethod.GET, "/api/v1/incidents/" + UUID.randomUUID(), null, Role.SRE, MAP).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
