package dev.cloudpilot.postmortems;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.cloudpilot.common.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-12: drafted by the system, edited and approved by a person, then locked. */
class PostmortemTest {

    private static final Instant T0 = Instant.parse("2026-11-15T12:00:00Z");

    private static final PostmortemContent DRAFT = new PostmortemContent(
            "Checkout failed for 18 minutes.", "About 30% of checkout requests failed.",
            "Release v2.14.0 of checkout-api broke payment intent creation.", "Rolled back to v2.13.4.",
            List.of("Add a contract test for the payment SDK", "Use canary releases for checkout-api"));

    private Postmortem postmortem;

    @BeforeEach
    void setUp() {
        postmortem = new Postmortem(UUID.randomUUID(), DRAFT, "template", null, "sre@cloudpilot.dev", T0);
    }

    @Test
    void startsAsADraftRecordingWhoAndWhatDraftedIt() {
        assertThat(postmortem.getStatus()).isEqualTo(Postmortem.Status.DRAFT);
        assertThat(postmortem.content()).isEqualTo(DRAFT);
        assertThat(postmortem.getDraftedBy()).isEqualTo("template");
        assertThat(postmortem.getCreatedBy()).isEqualTo("sre@cloudpilot.dev");
        assertThat(postmortem.getApprovedBy()).isNull();
    }

    @Test
    void aPersonCanEditTheDraft() {
        PostmortemContent edited = new PostmortemContent("New summary", DRAFT.impact(), DRAFT.rootCause(),
                DRAFT.resolution(), List.of("One action"));

        postmortem.edit(edited, "admin@cloudpilot.dev", T0.plusSeconds(60));

        assertThat(postmortem.content()).isEqualTo(edited);
        assertThat(postmortem.getUpdatedBy()).isEqualTo("admin@cloudpilot.dev");
        assertThat(postmortem.getUpdatedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void approvalLocksThePostmortem() {
        postmortem.approve("lead@cloudpilot.dev", T0.plusSeconds(120));

        assertThat(postmortem.getStatus()).isEqualTo(Postmortem.Status.APPROVED);
        assertThat(postmortem.getApprovedBy()).isEqualTo("lead@cloudpilot.dev");
        assertThat(postmortem.getApprovedAt()).isEqualTo(T0.plusSeconds(120));
        assertThatThrownBy(() -> postmortem.edit(DRAFT, "x", T0)).isInstanceOf(ConflictException.class)
                .hasMessage("This postmortem is approved and can no longer be changed");
        assertThatThrownBy(() -> postmortem.redraft(DRAFT, "claude", "claude-opus-5-5", "x", T0))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> postmortem.approve("x", T0)).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotApproveWithoutARootCauseOrPreventionActions() {
        postmortem.edit(new PostmortemContent("s", "i", " ", "r", List.of("a")), "x", T0);
        assertThatThrownBy(() -> postmortem.approve("lead", T0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("root cause");

        postmortem.edit(new PostmortemContent("s", "i", "cause", "r", List.of()), "x", T0);
        assertThatThrownBy(() -> postmortem.approve("lead", T0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prevention");
    }

    @Test
    void aDraftCanBeRedraftedAndRecordsTheNewSource() {
        postmortem.redraft(DRAFT, "claude", "claude-opus-5-5", "sre@cloudpilot.dev", T0.plusSeconds(30));

        assertThat(postmortem.getDraftedBy()).isEqualTo("claude");
        assertThat(postmortem.getModel()).isEqualTo("claude-opus-5-5");
    }

    @Test
    void preventionIsStoredOneActionPerLineAndBlankLinesAreIgnored() {
        postmortem.edit(new PostmortemContent("s", "i", "c", "r", List.of(" first ", "", "second")), "x", T0);

        assertThat(postmortem.content().prevention()).containsExactly("first", "second");
    }
}
