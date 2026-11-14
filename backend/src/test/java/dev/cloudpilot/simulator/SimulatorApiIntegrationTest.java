package dev.cloudpilot.simulator;

import java.util.List;
import java.util.Map;

import dev.cloudpilot.auth.Role;
import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** US-05: admins trigger failure scenarios for demos. */
class SimulatorApiIntegrationTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ScenarioManager scenarios;

    @AfterEach
    void stopScenario() {
        scenarios.stop();
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, Role role) {
        return rest.exchange(path, method, new HttpEntity<>(authHeaders(role)), Map.class);
    }

    @Test
    void anyUserCanSeeTheScenarioCatalogue() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/api/v1/simulator", Role.DEVELOPER);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> catalogue = (List<Map<String, Object>>) response.getBody().get("scenarios");
        assertThat(catalogue).extracting(s -> s.get("type")).containsExactly(
                "DB_CONNECTION_EXHAUSTION", "BAD_DEPLOYMENT", "QUEUE_BACKLOG", "POD_MEMORY_LEAK");
        assertThat(catalogue.getFirst()).containsKeys("title", "description", "durationMinutes", "affectedResources");
        assertThat(response.getBody().get("active")).isNull();
    }

    @Test
    void adminCanStartAndStopAScenario() {
        ResponseEntity<Map> started = call(HttpMethod.POST, "/api/v1/simulator/scenarios/BAD_DEPLOYMENT/start", Role.ADMIN);

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(started.getBody()).containsEntry("type", "BAD_DEPLOYMENT").containsEntry("startedBy", "it-admin@test.dev");
        Map<String, Object> active = (Map<String, Object>) call(HttpMethod.GET, "/api/v1/simulator", Role.SRE)
                .getBody().get("active");
        assertThat(active).containsEntry("type", "BAD_DEPLOYMENT");

        ResponseEntity<Map> stopped = call(HttpMethod.POST, "/api/v1/simulator/stop", Role.ADMIN);

        assertThat(stopped.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(scenarios.active()).isEmpty();
    }

    @Test
    void nonAdminsCannotStartScenarios() {
        assertThat(call(HttpMethod.POST, "/api/v1/simulator/scenarios/BAD_DEPLOYMENT/start", Role.SRE).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/api/v1/simulator/stop", Role.DEVELOPER).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scenarios.active()).isEmpty();
    }

    @Test
    void unknownScenarioIs400() {
        assertThat(call(HttpMethod.POST, "/api/v1/simulator/scenarios/ALIENS/start", Role.ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
