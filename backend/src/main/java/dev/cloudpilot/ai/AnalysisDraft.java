package dev.cloudpilot.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What an analyser proposes, before guardrails run. This record is also the JSON schema the
 * Claude API must answer in (structured outputs), so the descriptions below are part of the prompt.
 */
public record AnalysisDraft(
        @JsonPropertyDescription("Two or three sentences for the on-call engineer: what is happening and the most likely reason.")
        String summary,

        @JsonPropertyDescription("Facts taken directly from the evidence, each with the ids of the evidence items it comes from.")
        List<Claim> observedEvidence,

        @JsonPropertyDescription("The single most likely root cause, citing the evidence that supports it.")
        Claim likelyCause,

        @JsonPropertyDescription("How sure you are that the likely cause is right, from 0.0 to 1.0.")
        double confidence,

        @JsonPropertyDescription("One sentence: what makes the confidence this high or low, including what evidence is missing.")
        String confidenceReason,

        @JsonPropertyDescription("Read-only checks an engineer should run to confirm or rule out the likely cause, in order.")
        List<Step> verification,

        @JsonPropertyDescription("Safe remediation steps for a human to perform after verification, least risky first.")
        List<Step> remediation) {

    public record Claim(
            @JsonPropertyDescription("The statement.") String text,
            @JsonPropertyDescription("Ids of the evidence items that support it, e.g. [\"A1\", \"L3\"].") List<String> citations) {
    }

    public record Step(
            @JsonPropertyDescription("What to do, specific enough to act on.") String text,
            @JsonPropertyDescription("Ids of the evidence items, usually runbook sections, this step comes from.") List<String> citations) {
    }
}
