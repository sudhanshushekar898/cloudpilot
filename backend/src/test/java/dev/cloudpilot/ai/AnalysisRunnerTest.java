package dev.cloudpilot.ai;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisRunnerTest {

    private static final EvidenceBundle BUNDLE = new EvidenceBundle(UUID.randomUUID(), "INC-7", "title", "OPEN", "Why?",
            List.of("orders-db"), "orders-db", List.of(),
            List.of(new EvidenceItem("A1", EvidenceItem.Kind.ALERT, "orders-db", Instant.parse("2026-11-15T10:00:00Z"),
                    "orders-db cpu 97%", "First alert")));

    /** An analyser that returns a fixed draft or throws. */
    private static Analyzer analyzer(String name, String model, RuntimeException failure, double confidence) {
        return new Analyzer() {
            public String name() { return name; }
            public String model() { return model; }
            public AnalysisDraft analyze(EvidenceBundle bundle) {
                if (failure != null) {
                    throw failure;
                }
                return new AnalysisDraft(name + " summary", List.of(),
                        new AnalysisDraft.Claim("orders-db is saturated", List.of("A1", "Z9")), confidence, "r",
                        List.of(), List.of());
            }
        };
    }

    private final Analyzer fallback = analyzer("rule-based", null, null, 0.5);

    @Test
    void usesTheRuleBasedAnalyserWhenNoLlmIsConfigured() {
        AnalysisRunner.Outcome outcome = new AnalysisRunner(Optional.empty(), fallback, new AnalysisValidator()).run(BUNDLE);

        assertThat(outcome.analyzer()).isEqualTo("rule-based");
        assertThat(outcome.model()).isNull();
        assertThat(outcome.validated().summary()).isEqualTo("rule-based summary");
    }

    @Test
    void usesClaudeWhenItAnswers() {
        AnalysisRunner runner = new AnalysisRunner(Optional.of(analyzer("claude", "claude-opus-5-5", null, 0.8)), fallback,
                new AnalysisValidator());

        AnalysisRunner.Outcome outcome = runner.run(BUNDLE);

        assertThat(outcome.analyzer()).isEqualTo("claude");
        assertThat(outcome.model()).isEqualTo("claude-opus-5-5");
        assertThat(outcome.fellBack()).isFalse();
        assertThat(outcome.validated().confidence()).isEqualTo(0.8);
    }

    @Test
    void alwaysAppliesTheGuardrailsEvenToClaude() {
        AnalysisRunner runner = new AnalysisRunner(Optional.of(analyzer("claude", "claude-opus-5-5", null, 0.8)), fallback,
                new AnalysisValidator());

        AnalysisRunner.Outcome outcome = runner.run(BUNDLE);

        assertThat(outcome.validated().likelyCause().citations()).containsExactly("A1");
        assertThat(outcome.validated().warnings()).contains("Removed citation Z9: it does not refer to any evidence.");
    }

    @Test
    void fallsBackToRuleBasedWhenClaudeFails() {
        AnalysisRunner runner = new AnalysisRunner(
                Optional.of(analyzer("claude", "claude-opus-5-5", new IllegalStateException("timeout"), 0.8)), fallback,
                new AnalysisValidator());

        AnalysisRunner.Outcome outcome = runner.run(BUNDLE);

        assertThat(outcome.analyzer()).isEqualTo("rule-based");
        assertThat(outcome.fellBack()).isTrue();
        assertThat(outcome.validated().warnings())
                .contains("The AI model was unavailable, so this is the rule-based analysis instead.");
    }

    @Test
    void fallsBackToRuleBasedWhenClaudeRefuses() {
        AnalysisRunner runner = new AnalysisRunner(
                Optional.of(analyzer("claude", "claude-opus-5-5", new ClaudeRefusalException("cyber"), 0.8)), fallback,
                new AnalysisValidator());

        AnalysisRunner.Outcome outcome = runner.run(BUNDLE);

        assertThat(outcome.analyzer()).isEqualTo("rule-based");
        assertThat(outcome.validated().warnings())
                .contains("The AI model declined to answer, so this is the rule-based analysis instead.");
    }

    @Test
    void skipsClaudeWhileTheKillSwitchIsOff() {
        AnalysisRunner runner = new AnalysisRunner(Optional.of(analyzer("claude", "claude-opus-5-5", null, 0.8)), fallback,
                new AnalysisValidator(), () -> false);

        AnalysisRunner.Outcome outcome = runner.run(BUNDLE);

        assertThat(outcome.analyzer()).isEqualTo("rule-based");
        assertThat(outcome.fellBack()).isFalse();
        assertThat(outcome.validated().warnings())
                .contains("AI analysis is switched off by an admin, so this is the rule-based analysis.");
    }
}
