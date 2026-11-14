package dev.cloudpilot.knowledge;

import java.util.ArrayList;
import java.util.List;

import dev.cloudpilot.simulator.ScenarioType;
import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A small retrieval evaluation: for every failure scenario, search with only the evidence an
 * engineer would see during the incident (affected resources, metric names, log lines, never
 * the scenario's title) and check that the right runbook ranks first.
 *
 * If someone edits a runbook or the ranking and retrieval gets worse, this test fails.
 */
class RetrievalQualityIntegrationTest extends IntegrationTest {

    @Autowired
    private KnowledgeService knowledge;

    private static String evidenceFor(ScenarioType scenario) {
        List<String> evidence = new ArrayList<>();
        scenario.effects().forEach(e ->
                evidence.add(e.resourceName() + " " + e.metric().name().toLowerCase().replace('_', ' ')));
        scenario.logs().forEach(l -> evidence.add(l.message()));
        return String.join(" ", evidence);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "DB_CONNECTION_EXHAUSTION, orders-db-connection-exhaustion",
            "BAD_DEPLOYMENT,           bad-deployment-and-rollback",
            "QUEUE_BACKLOG,            payments-queue-backlog",
            "POD_MEMORY_LEAK,          pod-memory-leak-and-oomkilled-restarts",
    })
    void theRightRunbookRanksFirst(ScenarioType scenario, String expectedSlug) {
        List<RunbookSearch.Hit> hits = knowledge.search(evidenceFor(scenario), 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().runbookSlug()).isEqualTo(expectedSlug);
    }
}
