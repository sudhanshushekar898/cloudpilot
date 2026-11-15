package dev.cloudpilot.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR-0002 guardrails: citations must exist, unsupported causes lose confidence, risky steps need sign-off. */
class AnalysisValidatorTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    private final AnalysisValidator validator = new AnalysisValidator();

    private static final EvidenceBundle BUNDLE = new EvidenceBundle(UUID.randomUUID(), "INC-7", "title", "OPEN", "Why?",
            List.of("orders-db"), "orders-db", List.of(), List.of(
            new EvidenceItem("A1", EvidenceItem.Kind.ALERT, "orders-db", T0, "orders-db cpu 97%", "First alert"),
            new EvidenceItem("L1", EvidenceItem.Kind.LOG, "orders-db", T0, "too many clients already", "ERROR"),
            new EvidenceItem("R1", EvidenceItem.Kind.RUNBOOK, null, null, "Check connections.", "Orders DB > Verify")));

    private static AnalysisDraft.Claim claim(String text, String... citations) {
        return new AnalysisDraft.Claim(text, List.of(citations));
    }

    private static AnalysisDraft.Step step(String text, String... citations) {
        return new AnalysisDraft.Step(text, List.of(citations));
    }

    private static AnalysisDraft draft(AnalysisDraft.Claim cause, double confidence, List<AnalysisDraft.Step> remediation) {
        return new AnalysisDraft("summary", List.of(claim("CPU is high", "A1")), cause, confidence, "reason",
                List.of(step("Check active connections", "R1")), remediation);
    }

    private AnalysisValidator.Validated validate(AnalysisDraft draft) {
        return validator.validate(draft, BUNDLE);
    }

    @Test
    void aWellGroundedDraftPassesUnchanged() {
        AnalysisValidator.Validated result = validate(
                draft(claim("orders-db ran out of connections", "A1", "L1"), 0.8, List.of(step("Restart one checkout-api pod", "R1"))));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.8);
        assertThat(result.likelyCause().citations()).containsExactly("A1", "L1");
        assertThat(result.remediation()).singleElement().satisfies(s -> assertThat(s.requiresApproval()).isFalse());
    }

    @Test
    void citationsThatAreNotInTheEvidenceAreRemoved() {
        AnalysisValidator.Validated result = validate(
                draft(claim("orders-db ran out of connections", "A1", "X9"), 0.8, List.of()));

        assertThat(result.likelyCause().citations()).containsExactly("A1");
        assertThat(result.warnings()).contains("Removed citation X9: it does not refer to any evidence.");
    }

    @Test
    void anUncitedLikelyCauseLosesConfidence() {
        AnalysisValidator.Validated result = validate(draft(claim("Probably a network problem", "Q1"), 0.9, List.of()));

        assertThat(result.confidence()).isEqualTo(AnalysisValidator.UNSUPPORTED_CAUSE_MAX_CONFIDENCE);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).startsWith("The likely cause is not backed by cited evidence"));
    }

    @Test
    void observedEvidenceWithoutValidCitationsIsDropped() {
        AnalysisDraft draft = new AnalysisDraft("s", List.of(claim("CPU is high", "A1"), claim("Disk is full")),
                claim("cause", "A1"), 0.7, "r", List.of(), List.of());

        AnalysisValidator.Validated result = validate(draft);

        assertThat(result.observedEvidence()).extracting(AnalysisDraft.Claim::text).containsExactly("CPU is high");
        assertThat(result.warnings()).contains("Dropped an observation with no supporting evidence: \"Disk is full\"");
    }

    @Test
    void confidenceIsClampedToTheZeroToOneRange() {
        assertThat(validate(draft(claim("c", "A1"), 7.5, List.of())).confidence()).isEqualTo(1.0);
        assertThat(validate(draft(claim("c", "A1"), -1, List.of())).confidence()).isEqualTo(0.0);
        assertThat(validate(draft(claim("c", "A1"), Double.NaN, List.of())).confidence()).isEqualTo(0.0);
    }

    @Test
    void destructiveRemediationRequiresExplicitApproval() {
        AnalysisValidator.Validated result = validate(draft(claim("c", "A1"), 0.7, List.of(
                step("Restart one checkout-api pod at a time", "R1"),
                step("Run SELECT pg_terminate_backend(pid) for idle sessions", "R1"),
                step("DROP TABLE orders_archive to free space"),
                step("kubectl delete pod -l app=payments-worker"),
                step("Purge payments-queue"))));

        assertThat(result.remediation()).extracting(AnalysisResult.RemediationStep::requiresApproval)
                .containsExactly(false, true, true, true, true);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("4 remediation steps change or delete"));
    }

    @Test
    void missingListsAreTreatedAsEmpty() {
        AnalysisDraft draft = new AnalysisDraft(null, null, null, 0.5, null, null, null);

        AnalysisValidator.Validated result = validate(draft);

        assertThat(result.observedEvidence()).isEmpty();
        assertThat(result.verification()).isEmpty();
        assertThat(result.remediation()).isEmpty();
        assertThat(result.summary()).isEmpty();
        assertThat(result.confidence()).isLessThanOrEqualTo(AnalysisValidator.UNSUPPORTED_CAUSE_MAX_CONFIDENCE);
    }
}
