package dev.cloudpilot.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A validated analysis, as stored and shown. Produced from an {@link AnalysisDraft} by
 * {@link AnalysisValidator}, so every citation here points at real evidence.
 *
 * @param analyzer "claude" or "rule-based"
 * @param model    the model id when an LLM was used, otherwise null
 * @param warnings what the guardrails changed or want a human to know
 */
public record AnalysisResult(UUID id, UUID incidentId, String question, String summary,
                             List<AnalysisDraft.Claim> observedEvidence, AnalysisDraft.Claim likelyCause,
                             double confidence, String confidenceReason, List<AnalysisDraft.Step> verification,
                             List<RemediationStep> remediation, List<String> warnings, List<EvidenceItem> evidence,
                             String analyzer, String model, String requestedBy, Instant createdAt) {

    /** A remediation step plus whether the guardrails want explicit human sign-off before it. */
    public record RemediationStep(String text, List<String> citations, boolean requiresApproval) {
    }
}
