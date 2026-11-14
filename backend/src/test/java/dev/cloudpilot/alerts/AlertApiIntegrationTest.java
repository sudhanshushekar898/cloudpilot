package dev.cloudpilot.alerts;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudpilot.auth.Role;
import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** US-06: alert rules managed by admins; alerts visible to everyone. */
class AlertApiIntegrationTest extends IntegrationTest {

    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID API_ERROR_RULE = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AlertRepository alerts;

    @Autowired
    private AlertRuleRepository rules;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from alerts");
        jdbc.update("delete from alert_rules where name like 'IT %'");
        jdbc.update("update alert_rules set enabled = true, threshold = 5 where id = ?", API_ERROR_RULE);
    }

    private <T> ResponseEntity<T> call(HttpMethod method, String path, Object body, Role role,
                                       ParameterizedTypeReference<T> type) {
        return rest.exchange(path, method, new HttpEntity<>(body, authHeaders(role)), type);
    }

    private ResponseEntity<Map<String, Object>> send(HttpMethod method, String path, Object body, Role role) {
        return call(method, path, body, role, new ParameterizedTypeReference<>() {});
    }

    private static Map<String, Object> ruleRequest(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("metric", "CPU");
        body.put("operator", "GT");
        body.put("threshold", 80);
        body.put("durationMinutes", 5);
        body.put("severity", "WARNING");
        body.put("resourceType", "EC2");
        body.put("enabled", true);
        return body;
    }

    @Test
    void anyUserCanListRules() {
        ResponseEntity<List<Map<String, Object>>> response = call(HttpMethod.GET, "/api/v1/alert-rules", null,
                Role.DEVELOPER, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(response.getBody()).anySatisfy(rule -> assertThat(rule)
                .containsEntry("name", "API error rate above 5%")
                .containsEntry("durationMinutes", 5)
                .containsEntry("resourceType", "API"));
    }

    @Test
    void adminCanCreateARule() {
        ResponseEntity<Map<String, Object>> response =
                send(HttpMethod.POST, "/api/v1/alert-rules", ruleRequest("IT web CPU high"), Role.ADMIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("name", "IT web CPU high").containsEntry("threshold", 80.0);
        assertThat(rules.existsByNameIgnoreCase("IT web CPU high")).isTrue();
    }

    @Test
    void sreAndDeveloperCannotChangeRules() {
        assertThat(send(HttpMethod.POST, "/api/v1/alert-rules", ruleRequest("IT nope"), Role.SRE).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.PUT, "/api/v1/alert-rules/" + API_ERROR_RULE, ruleRequest("IT nope"), Role.DEVELOPER)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidRulesAreRejected() {
        Map<String, Object> body = ruleRequest("IT bad");
        body.put("durationMinutes", 0);

        assertThat(send(HttpMethod.POST, "/api/v1/alert-rules", body, Role.ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateRuleNamesConflict() {
        assertThat(send(HttpMethod.POST, "/api/v1/alert-rules", ruleRequest("API error rate above 5%"), Role.ADMIN)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void disablingARuleResolvesItsFiringAlerts() {
        alerts.save(new Alert(API_ERROR_RULE, CHECKOUT_API, Severity.CRITICAL, "checkout-api error rate 18% > 5% for 5 min",
                18, Instant.now()));
        Map<String, Object> body = ruleRequest("API error rate above 5%");
        body.put("metric", "ERROR_RATE");
        body.put("threshold", 5);
        body.put("resourceType", "API");
        body.put("severity", "CRITICAL");
        body.put("enabled", false);

        ResponseEntity<Map<String, Object>> response =
                send(HttpMethod.PUT, "/api/v1/alert-rules/" + API_ERROR_RULE, body, Role.ADMIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("enabled", false);
        assertThat(alerts.findAll()).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(Alert.Status.RESOLVED));
    }

    @Test
    void listsAlertsWithResourceAndRuleNamesFilteredByStatus() {
        Alert firing = alerts.save(new Alert(API_ERROR_RULE, CHECKOUT_API, Severity.CRITICAL, "firing one", 18, Instant.now()));
        Alert resolved = new Alert(API_ERROR_RULE, CHECKOUT_API, Severity.CRITICAL, "old one", 12,
                Instant.now().minusSeconds(3600));
        resolved.resolve(Instant.now().minusSeconds(3000));
        alerts.save(resolved);

        ResponseEntity<List<Map<String, Object>>> response = call(HttpMethod.GET, "/api/v1/alerts?status=FIRING", null,
                Role.DEVELOPER, new ParameterizedTypeReference<>() {});

        assertThat(response.getBody()).singleElement().satisfies(alert -> assertThat(alert)
                .containsEntry("id", firing.getId().toString())
                .containsEntry("resourceName", "checkout-api")
                .containsEntry("ruleName", "API error rate above 5%")
                .containsEntry("severity", "CRITICAL"));

        ResponseEntity<List<Map<String, Object>>> all = call(HttpMethod.GET, "/api/v1/alerts", null,
                Role.DEVELOPER, new ParameterizedTypeReference<>() {});
        assertThat(all.getBody()).extracting(a -> a.get("message")).containsExactly("firing one", "old one");
    }

    @Test
    void summaryCountsFiringAlertsBySeverity() {
        alerts.save(new Alert(API_ERROR_RULE, CHECKOUT_API, Severity.CRITICAL, "x", 18, Instant.now()));

        ResponseEntity<Map<String, Object>> response = send(HttpMethod.GET, "/api/v1/alerts/summary", null, Role.SRE);

        assertThat(response.getBody()).containsEntry("firing", 1).containsEntry("critical", 1).containsEntry("warning", 0);
    }
}
