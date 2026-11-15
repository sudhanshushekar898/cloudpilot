package dev.cloudpilot.postmortems;

import java.util.List;

import dev.cloudpilot.ai.ClaudeGateway;

import static dev.cloudpilot.common.Xml.escape;

/**
 * Drafts the narrative parts of a postmortem with Claude, from recorded facts only. The
 * timeline stays out of the model's hands entirely. Any field the model leaves blank is
 * filled from the template, so a draft is always complete; a person edits and approves it.
 */
public class ClaudePostmortemWriter {

    private static final String SYSTEM = """
            You write blameless incident postmortems for an engineering team.

            Rules:
            - Use only the facts in the user message. Every number (durations, percentages, counts)
              must appear there; never estimate customer numbers or revenue.
            - Blameless: describe systems and decisions, never blame people.
            - The root cause must be consistent with the recorded resolution and the analysis. If the
              facts don't establish it, say what is known and that it must be confirmed.
            - Prevention actions are concrete and checkable (an alert, a test, a release practice, a
              runbook change), two to five of them.
            - Plain, short sentences. No headings or Markdown inside fields.
            """;

    private final ClaudeGateway gateway;
    private final TemplatePostmortemWriter template = new TemplatePostmortemWriter();

    public ClaudePostmortemWriter(ClaudeGateway gateway) {
        this.gateway = gateway;
    }

    public String model() {
        return gateway.model();
    }

    public PostmortemContent write(PostmortemFacts facts) {
        PostmortemContent draft = gateway.complete(SYSTEM, user(facts), PostmortemContent.class);
        PostmortemContent fallback = template.write(facts);
        return new PostmortemContent(
                orElse(draft.summary(), fallback.summary()),
                orElse(draft.impact(), fallback.impact()),
                orElse(draft.rootCause(), fallback.rootCause()),
                orElse(draft.resolution(), fallback.resolution()),
                draft.prevention() == null || draft.prevention().stream().allMatch(s -> s == null || s.isBlank())
                        ? fallback.prevention() : draft.prevention());
    }

    static String user(PostmortemFacts f) {
        StringBuilder out = new StringBuilder("<facts>\n");
        line(out, "Incident", f.incidentKey() + " (" + f.severity() + "): " + f.title());
        line(out, "Opened", f.openedAt().toString());
        line(out, "Resolved", f.resolvedAt().toString());
        line(out, "Duration", TemplatePostmortemWriter.minutes(f.duration()));
        if (f.timeToAcknowledge() != null) {
            line(out, "Time to acknowledge", TemplatePostmortemWriter.minutes(f.timeToAcknowledge()));
        }
        line(out, "Affected resources", String.join(", ", f.resources()));
        line(out, "Probable origin", String.valueOf(f.probableOrigin()));
        list(out, "Alerts", f.alerts());
        list(out, "Deployments around the incident", f.deployments());
        if (f.likelyCause() != null) {
            line(out, "Root-cause analysis (" + f.likelyCause().analyzer() + ", "
                    + Math.round(f.likelyCause().confidence() * 100) + "% confidence)", f.likelyCause().text());
        }
        line(out, "Recorded resolution", f.resolutionNote());
        list(out, "Team comments", f.comments());
        list(out, "Timeline", f.timeline().stream().map(t -> t.at() + " " + t.text()).toList());
        out.append("</facts>\nWrite the postmortem fields.");
        return out.toString();
    }

    private static void line(StringBuilder out, String label, String value) {
        out.append(label).append(": ").append(escape(value)).append('\n');
    }

    private static void list(StringBuilder out, String label, List<String> values) {
        out.append(label).append(":\n");
        if (values.isEmpty()) {
            out.append("- none\n");
        }
        values.forEach(v -> out.append("- ").append(escape(v)).append('\n'));
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
