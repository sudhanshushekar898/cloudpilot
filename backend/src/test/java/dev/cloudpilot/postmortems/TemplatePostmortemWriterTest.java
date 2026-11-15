package dev.cloudpilot.postmortems;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplatePostmortemWriterTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    private final TemplatePostmortemWriter writer = new TemplatePostmortemWriter();

    private static PostmortemFacts facts(PostmortemFacts.LikelyCause cause, List<String> deployments) {
        return new PostmortemFacts("INC-7", "checkout-api error rate 38% > 5% for 5 min", "CRITICAL", T0,
                T0.plusSeconds(180), T0.plusSeconds(1080), "Rolled back checkout-api to v2.13.4",
                List.of("checkout-api", "web-frontend"), "checkout-api",
                List.of("checkout-api error rate 38% > 5% for 5 min", "web-frontend error rate 9% > 5% for 3 min"),
                cause, deployments, List.of(), List.of());
    }

    @Test
    void summaryAndImpactUseOnlyRecordedFacts() {
        PostmortemContent draft = writer.write(facts(null, List.of()));

        assertThat(draft.summary()).isEqualTo("INC-7 (CRITICAL): checkout-api error rate 38% > 5% for 5 min. "
                + "It lasted 18 min and was resolved: Rolled back checkout-api to v2.13.4.");
        assertThat(draft.impact()).contains("checkout-api, web-frontend").contains("18 min")
                .contains("acknowledged after 3 min").contains("2 alerts");
        assertThat(draft.resolution()).isEqualTo("Rolled back checkout-api to v2.13.4");
    }

    @Test
    void rootCauseQuotesTheAnalysisAndAsksForConfirmation() {
        PostmortemContent draft = writer.write(facts(new PostmortemFacts.LikelyCause(
                "Release v2.14.0 of checkout-api broke payment intent creation", 0.6, "rule-based"), List.of()));

        assertThat(draft.rootCause()).startsWith("Release v2.14.0 of checkout-api broke payment intent creation")
                .contains("rule-based analysis, 60% confidence").contains("Confirm or correct");
    }

    @Test
    void withoutAnAnalysisTheRootCauseIsLeftForAPersonToWrite() {
        PostmortemContent draft = writer.write(facts(null, List.of()));

        assertThat(draft.rootCause()).startsWith("To be written.").contains("probable origin was checkout-api");
    }

    @Test
    void preventionActionsFollowFromWhatHappened() {
        PostmortemContent draft = writer.write(facts(null, List.of("checkout-api: Deployed v2.14.0: Switch to new payment provider SDK")));

        assertThat(draft.prevention()).anySatisfy(a -> assertThat(a).contains("canary").contains("checkout-api"));
        assertThat(draft.prevention()).anySatisfy(a -> assertThat(a).contains("runbook"));
        assertThat(draft.prevention()).isNotEmpty();
    }
}
