package dev.cloudpilot.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeAnalyzerTest {

    private static final EvidenceBundle BUNDLE = new EvidenceBundle(UUID.randomUUID(), "INC-7", "title", "OPEN", "Why?",
            List.of("orders-db"), "orders-db", List.of(),
            List.of(new EvidenceItem("A1", EvidenceItem.Kind.ALERT, "orders-db", Instant.parse("2026-11-15T10:00:00Z"),
                    "orders-db cpu 97%", "First alert")));

    private static final AnalysisDraft ANSWER = new AnalysisDraft("summary", List.of(),
            new AnalysisDraft.Claim("orders-db is saturated", List.of("A1")), 0.7, "reason", List.of(), List.of());

    /** Records what it was asked and returns a canned answer. */
    static final class FakeGateway implements ClaudeGateway {
        String system;
        String user;
        RuntimeException failWith;
        Class<?> requestedType;

        @Override
        public <T> T complete(String system, String user, Class<T> type) {
            this.requestedType = type;
            this.system = system;
            this.user = user;
            if (failWith != null) {
                throw failWith;
            }
            return type.cast(ANSWER);
        }

        @Override
        public String model() {
            return "claude-opus-5-5";
        }
    }

    @Test
    void sendsTheFixedSystemPromptAndTheIncidentEvidence() {
        FakeGateway gateway = new FakeGateway();
        ClaudeAnalyzer analyzer = new ClaudeAnalyzer(gateway, new PromptBuilder());

        AnalysisDraft draft = analyzer.analyze(BUNDLE);

        assertThat(draft).isEqualTo(ANSWER);
        assertThat(gateway.system).isEqualTo(new PromptBuilder().system());
        assertThat(gateway.user).contains("id=\"A1\"").contains("INC-7");
        assertThat(gateway.requestedType).isEqualTo(AnalysisDraft.class);
        assertThat(analyzer.name()).isEqualTo("claude");
        assertThat(analyzer.model()).isEqualTo("claude-opus-5-5");
    }

    @Test
    void refusalsAreSurfacedToTheCaller() {
        FakeGateway gateway = new FakeGateway();
        gateway.failWith = new ClaudeRefusalException("cyber");

        assertThatThrownBy(() -> new ClaudeAnalyzer(gateway, new PromptBuilder()).analyze(BUNDLE))
                .isInstanceOf(ClaudeRefusalException.class)
                .hasMessageContaining("cyber");
    }
}
