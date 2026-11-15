package dev.cloudpilot.postmortems;

import java.time.Instant;
import java.util.List;

import dev.cloudpilot.ai.ClaudeGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudePostmortemWriterTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    private static final PostmortemFacts FACTS = new PostmortemFacts("INC-7", "checkout-api errors", "CRITICAL", T0,
            T0.plusSeconds(180), T0.plusSeconds(1080), "Rolled back <checkout-api> to v2.13.4",
            List.of("checkout-api"), "checkout-api", List.of("checkout-api error rate 38% > 5% for 5 min"),
            new PostmortemFacts.LikelyCause("Release v2.14.0 broke payments", 0.6, "claude"),
            List.of("checkout-api: Deployed v2.14.0"), List.of("Seeing NPEs in PaymentClient"),
            List.of(new PostmortemFacts.TimelineLine(T0, "Alert fired: checkout-api error rate 38%")));

    static final class FakeGateway implements ClaudeGateway {
        String system;
        String user;
        Object answer;

        @Override
        public <T> T complete(String system, String user, Class<T> type) {
            this.system = system;
            this.user = user;
            return type.cast(answer);
        }

        @Override
        public String model() {
            return "claude-opus-5-5";
        }
    }

    @Test
    void sendsBlamelessInstructionsAndEscapedFacts() {
        FakeGateway gateway = new FakeGateway();
        gateway.answer = new PostmortemContent("s", "i", "c", "r", List.of("a"));

        new ClaudePostmortemWriter(gateway).write(FACTS);

        assertThat(gateway.system).contains("blameless").contains("Use only the facts");
        assertThat(gateway.user).contains("INC-7").contains("Duration: 18 min").contains("Time to acknowledge: 3 min")
                .contains("Rolled back &lt;checkout-api&gt; to v2.13.4")
                .contains("Release v2.14.0 broke payments")
                .contains("Seeing NPEs in PaymentClient");
    }

    @Test
    void fillsBlankFieldsFromTheTemplateSoNothingIsMissing() {
        FakeGateway gateway = new FakeGateway();
        gateway.answer = new PostmortemContent("Model summary", "  ", "Model root cause", null, List.of());

        PostmortemContent draft = new ClaudePostmortemWriter(gateway).write(FACTS);

        TemplatePostmortemWriter template = new TemplatePostmortemWriter();
        assertThat(draft.summary()).isEqualTo("Model summary");
        assertThat(draft.rootCause()).isEqualTo("Model root cause");
        assertThat(draft.impact()).isEqualTo(template.write(FACTS).impact());
        assertThat(draft.resolution()).isEqualTo(FACTS.resolutionNote());
        assertThat(draft.prevention()).isEqualTo(template.write(FACTS).prevention());
    }
}
