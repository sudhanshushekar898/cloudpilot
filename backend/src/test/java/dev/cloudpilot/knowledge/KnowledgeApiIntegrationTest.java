package dev.cloudpilot.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudpilot.alerts.AlertFired;
import dev.cloudpilot.alerts.Severity;
import dev.cloudpilot.auth.Role;
import dev.cloudpilot.incidents.IncidentRepository;
import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
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

/** US-10: runbooks are searchable and suggested for incidents (the "R" in RAG). */
class KnowledgeApiIntegrationTest extends IntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from runbooks where created_by <> 'system'");
        jdbc.update("delete from incidents");
    }

    private <T> ResponseEntity<T> call(HttpMethod method, String path, Object body, Role role,
                                       ParameterizedTypeReference<T> type) {
        return rest.exchange(path, method, new HttpEntity<>(body, authHeaders(role)), type);
    }

    private List<Map<String, Object>> search(String q) {
        return call(HttpMethod.GET, "/api/v1/runbooks/search?q=" + q, null, Role.DEVELOPER, LIST).getBody();
    }

    @Test
    void seededRunbooksAreListed() {
        List<Map<String, Object>> runbooks = call(HttpMethod.GET, "/api/v1/runbooks", null, Role.DEVELOPER, LIST).getBody();

        assertThat(runbooks).extracting(r -> r.get("slug")).contains(
                "orders-db-connection-exhaustion", "bad-deployment-rollback", "payments-queue-backlog",
                "pod-memory-leak-oomkilled", "api-high-latency-triage");
        assertThat(runbooks.getFirst()).containsKeys("title", "chunkCount", "updatedAt");
    }

    @Test
    void searchFindsTheRelevantSectionWithAHighlightedSnippet() {
        List<Map<String, Object>> hits = search("too many clients already");

        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst()).containsEntry("runbookSlug", "orders-db-connection-exhaustion")
                .containsEntry("heading", "Orders DB connection exhaustion > Symptoms");
        assertThat((String) hits.getFirst().get("snippet")).contains("[[");
    }

    @Test
    void searchWithNothingSearchableReturnsNoHits() {
        assertThat(search("97%")).isEmpty();
    }

    @Test
    void runbookCanBeReadInFull() {
        Map<String, Object> runbook = call(HttpMethod.GET, "/api/v1/runbooks/payments-queue-backlog", null,
                Role.DEVELOPER, MAP).getBody();

        assertThat(runbook).containsEntry("title", "Payments queue backlog");
        assertThat((String) runbook.get("content")).contains("Never purge the queue");
    }

    @Test
    void adminCanUploadAMarkdownRunbookThatBecomesSearchable() {
        String markdown = "# Redis eviction storm\n\n## Symptoms\n\nKeys evicted, cache hit ratio collapses.\n";

        ResponseEntity<Map<String, Object>> created = call(HttpMethod.POST, "/api/v1/runbooks",
                Map.of("title", "Redis eviction storm", "content", markdown), Role.ADMIN, MAP);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("slug", "redis-eviction-storm").containsEntry("chunkCount", 1);
        assertThat(search("eviction")).extracting(h -> h.get("runbookSlug")).containsExactly("redis-eviction-storm");
    }

    @Test
    void onlyAdminsCanUploadOrDelete() {
        assertThat(call(HttpMethod.POST, "/api/v1/runbooks", Map.of("title", "Nope", "content", "x"), Role.SRE, MAP)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.DELETE, "/api/v1/runbooks/payments-queue-backlog", null, Role.DEVELOPER, MAP)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateTitlesConflict() {
        assertThat(call(HttpMethod.POST, "/api/v1/runbooks",
                Map.of("title", "Payments queue backlog", "content", "# dup"), Role.ADMIN, MAP).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void adminCanDeleteAnUploadedRunbook() {
        call(HttpMethod.POST, "/api/v1/runbooks", Map.of("title", "Temp runbook", "content", "## A\n\ntext"), Role.ADMIN, MAP);

        assertThat(call(HttpMethod.DELETE, "/api/v1/runbooks/temp-runbook", null, Role.ADMIN, MAP).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.GET, "/api/v1/runbooks/temp-runbook", null, Role.ADMIN, MAP).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void incidentSuggestionsPointToTheMatchingRunbook() {
        events.publishEvent(new AlertFired(UUID.randomUUID(), UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"), Severity.CRITICAL,
                "orders-db errors connections exhausted", Instant.now()));
        UUID incidentId = incidents.findAll().getFirst().getId();

        List<Map<String, Object>> suggestions = call(HttpMethod.GET,
                "/api/v1/runbooks/suggestions?incidentId=" + incidentId, null, Role.DEVELOPER, LIST).getBody();

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.getFirst()).containsEntry("runbookSlug", "orders-db-connection-exhaustion");
    }
}
