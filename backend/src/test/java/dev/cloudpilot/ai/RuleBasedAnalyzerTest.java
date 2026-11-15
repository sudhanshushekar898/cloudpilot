package dev.cloudpilot.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedAnalyzerTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    private final RuleBasedAnalyzer analyzer = new RuleBasedAnalyzer();
    private final AnalysisValidator validator = new AnalysisValidator();

    private static EvidenceItem item(String id, EvidenceItem.Kind kind, String resource, int seconds, String text, String source) {
        return new EvidenceItem(id, kind, resource, kind == EvidenceItem.Kind.RUNBOOK ? null : T0.plusSeconds(seconds), text, source);
    }

    /** DB pool exhaustion, plus an unrelated catalog-api release 4 minutes earlier (the red herring). */
    private static EvidenceBundle dbExhaustion() {
        return new EvidenceBundle(UUID.randomUUID(), "INC-1", "orders-db cpu 97% > 90% for 3 min", "OPEN", "Why?",
                List.of("orders-db", "checkout-api", "web-frontend"), "orders-db",
                List.of("checkout-api -> orders-db", "web-frontend -> checkout-api"),
                List.of(
                        item("A1", EvidenceItem.Kind.ALERT, "orders-db", 0, "orders-db cpu 97% > 90% for 3 min", "First alert"),
                        item("A2", EvidenceItem.Kind.ALERT, "checkout-api", 60, "checkout-api error rate 18% > 5% for 5 min", "depends"),
                        item("D1", EvidenceItem.Kind.DEPLOYMENT, "catalog-api", -240, "Deployed v3.2.1: Update product image CDN host", "ci-pipeline"),
                        item("L1", EvidenceItem.Kind.LOG, "orders-db", 10, "too many clients already: max_connections (100) reached", "WARN"),
                        item("L2", EvidenceItem.Kind.LOG, "checkout-api", 20, "HikariPool-1 - Connection is not available", "ERROR"),
                        item("R1", EvidenceItem.Kind.RUNBOOK, null, 0,
                                "1. Check active connections against the limit.\n2. Find which service holds the connections.\n3. Look for idle-in-transaction sessions.",
                                "Orders DB connection exhaustion > Verify"),
                        item("R2", EvidenceItem.Kind.RUNBOOK, null, 0,
                                "- If one client holds most connections, scale that client down or restart its pods.\n- Terminate only sessions that are idle in transaction for more than 5 minutes.\n- Do **not** raise `max_connections` during the incident.",
                                "Orders DB connection exhaustion > Safe mitigation")));
    }

    private static EvidenceBundle badDeployment() {
        return new EvidenceBundle(UUID.randomUUID(), "INC-2", "checkout-api error rate 38% > 5% for 5 min", "OPEN", "Why?",
                List.of("checkout-api", "web-frontend"), "checkout-api", List.of("web-frontend -> checkout-api"),
                List.of(
                        item("A1", EvidenceItem.Kind.ALERT, "checkout-api", 0, "checkout-api error rate 38% > 5% for 5 min", "First alert"),
                        item("D1", EvidenceItem.Kind.DEPLOYMENT, "checkout-api", -120, "Deployed v2.14.0: Switch to new payment provider SDK", "ci-pipeline"),
                        item("L1", EvidenceItem.Kind.LOG, "checkout-api", 30, "java.lang.NullPointerException at PaymentClient.createIntent", "ERROR")));
    }

    @Test
    void blamesTheDatabaseNotTheUnrelatedDeployment() {
        AnalysisDraft draft = analyzer.analyze(dbExhaustion());

        assertThat(draft.likelyCause().text()).contains("orders-db").doesNotContain("catalog-api");
        assertThat(draft.likelyCause().citations()).contains("A1", "L1").doesNotContain("D1");
        assertThat(draft.observedEvidence()).anySatisfy(claim -> {
            assertThat(claim.text()).contains("catalog-api").contains("not one of the affected resources");
            assertThat(claim.citations()).containsExactly("D1");
        });
    }

    @Test
    void blamesADeploymentToTheOriginResource() {
        AnalysisDraft draft = analyzer.analyze(badDeployment());

        assertThat(draft.likelyCause().text()).contains("v2.14.0").contains("checkout-api");
        assertThat(draft.likelyCause().citations()).contains("D1", "A1");
        assertThat(draft.confidence()).isGreaterThan(0.5);
        assertThat(draft.remediation()).anySatisfy(step -> assertThat(step.text()).containsIgnoringCase("roll back"));
    }

    @Test
    void takesVerificationAndRemediationFromTheRunbookAndCitesIt() {
        AnalysisDraft draft = analyzer.analyze(dbExhaustion());

        assertThat(draft.verification()).extracting(AnalysisDraft.Step::text)
                .containsExactly("Check active connections against the limit.", "Find which service holds the connections.",
                        "Look for idle-in-transaction sessions.");
        assertThat(draft.verification()).allSatisfy(s -> assertThat(s.citations()).containsExactly("R1"));
        assertThat(draft.remediation()).hasSize(3).allSatisfy(s -> assertThat(s.citations()).containsExactly("R2"));
    }

    @Test
    void itsOutputPassesTheGuardrailsWithoutCitationWarnings() {
        AnalysisValidator.Validated validated = validator.validate(analyzer.analyze(dbExhaustion()), dbExhaustion());

        assertThat(validated.warnings()).noneMatch(w -> w.startsWith("Removed citation"));
        // The runbook's "terminate idle sessions" step is still flagged for human approval.
        assertThat(validated.remediation()).filteredOn(AnalysisResult.RemediationStep::requiresApproval).hasSize(1);
    }

    @Test
    void reportsLowConfidenceWhenThereIsLittleEvidence() {
        EvidenceBundle thin = new EvidenceBundle(UUID.randomUUID(), "INC-3", "t", "OPEN", "Why?", List.of("orders-db"),
                "orders-db", List.of(),
                List.of(item("A1", EvidenceItem.Kind.ALERT, "orders-db", 0, "orders-db cpu high", "First alert")));

        AnalysisDraft draft = analyzer.analyze(thin);

        assertThat(draft.confidence()).isLessThan(0.5);
        assertThat(draft.verification()).isNotEmpty();
    }
}
