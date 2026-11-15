package dev.cloudpilot.postmortems;

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

/** US-12 through the API: draft after resolution, human edit, approval locks it. */
class PostmortemApiIntegrationTest extends IntegrationTest {

    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");
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

    private String base;

    @BeforeEach
    void openIncident() {
        events.publishEvent(new AlertFired(UUID.randomUUID(), RULE, CHECKOUT_API, Severity.CRITICAL,
                "checkout-api error rate 38% > 5% for 5 min", Instant.now().minus(20, ChronoUnit.MINUTES)));
        base = "/api/v1/incidents/" + incidents.findAll().getFirst().getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from incidents");
    }

    private ResponseEntity<Map<String, Object>> call(HttpMethod method, String path, Object body, Role role) {
        return rest.exchange(base + path, method, new HttpEntity<>(body, authHeaders(role)), MAP);
    }

    private void resolveIncident() {
        call(HttpMethod.POST, "/acknowledge", null, Role.SRE);
        call(HttpMethod.POST, "/investigate", null, Role.SRE);
        call(HttpMethod.POST, "/resolve", Map.of("resolution", "Rolled back checkout-api to v2.13.4"), Role.SRE);
    }

    private static Map<String, Object> edit(String rootCause) {
        return Map.of("summary", "Checkout failed for 20 minutes.", "impact", "About 38% of checkouts failed.",
                "rootCause", rootCause, "resolution", "Rolled back checkout-api to v2.13.4",
                "prevention", List.of("Add a contract test for the payment SDK", "Canary releases for checkout-api"));
    }

    @Test
    void cannotDraftBeforeTheIncidentIsResolved() {
        ResponseEntity<Map<String, Object>> response = call(HttpMethod.POST, "/postmortem/generate", null, Role.SRE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("detail", "Resolve the incident before writing its postmortem");
    }

    @Test
    void draftsFromRecordedFactsAfterResolution() {
        resolveIncident();

        ResponseEntity<Map<String, Object>> response = call(HttpMethod.POST, "/postmortem/generate", null, Role.SRE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> pm = response.getBody();
        assertThat(pm).containsEntry("status", "DRAFT").containsEntry("draftedBy", "template")
                .containsEntry("resolution", "Rolled back checkout-api to v2.13.4");
        assertThat((String) pm.get("summary")).startsWith("INC-");
        assertThat((List<Map<String, Object>>) pm.get("timeline")).extracting(t -> t.get("text"))
                .contains("Status changed from INVESTIGATING to RESOLVED");
    }

    @Test
    void developersCanReadButNotDraftOrApprove() {
        resolveIncident();
        call(HttpMethod.POST, "/postmortem/generate", null, Role.SRE);

        assertThat(call(HttpMethod.GET, "/postmortem", null, Role.DEVELOPER).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.POST, "/postmortem/generate", null, Role.DEVELOPER).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/postmortem/approve", null, Role.DEVELOPER).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anSreEditsThenApprovesAndThePostmortemIsLocked() {
        resolveIncident();
        call(HttpMethod.POST, "/postmortem/generate", null, Role.SRE);

        ResponseEntity<Map<String, Object>> edited = call(HttpMethod.PUT, "/postmortem",
                edit("Release v2.14.0 broke payment intent creation."), Role.SRE);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody()).containsEntry("rootCause", "Release v2.14.0 broke payment intent creation.")
                .containsEntry("updatedBy", "it-sre@test.dev");

        ResponseEntity<Map<String, Object>> approved = call(HttpMethod.POST, "/postmortem/approve", null, Role.ADMIN);
        assertThat(approved.getBody()).containsEntry("status", "APPROVED").containsEntry("approvedBy", "it-admin@test.dev");

        assertThat(call(HttpMethod.PUT, "/postmortem", edit("changed"), Role.SRE).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.POST, "/postmortem/generate", null, Role.SRE).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cannotApproveWithoutARootCause() {
        resolveIncident();
        call(HttpMethod.POST, "/postmortem/generate", null, Role.SRE);
        call(HttpMethod.PUT, "/postmortem", edit(" "), Role.SRE);

        assertThat(call(HttpMethod.POST, "/postmortem/approve", null, Role.SRE).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void noPostmortemYetIs404() {
        assertThat(call(HttpMethod.GET, "/postmortem", null, Role.SRE).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
