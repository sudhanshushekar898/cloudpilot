package dev.cloudpilot.postmortems;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Drafts a postmortem from the recorded facts with fixed wording. No API key needed; it is
 * also the fallback when the AI draft fails. It never guesses: where a fact is missing (no
 * root-cause analysis yet), it says what a person needs to write.
 */
public class TemplatePostmortemWriter {

    public PostmortemContent write(PostmortemFacts facts) {
        String duration = minutes(facts.duration());

        String summary = "%s (%s): %s. It lasted %s and was resolved: %s.".formatted(
                facts.incidentKey(), facts.severity(), facts.title(), duration, facts.resolutionNote());

        StringBuilder impact = new StringBuilder()
                .append("Affected resources: ").append(String.join(", ", facts.resources())).append(". ")
                .append("The incident lasted ").append(duration);
        if (facts.timeToAcknowledge() != null) {
            impact.append(" and was acknowledged after ").append(minutes(facts.timeToAcknowledge()));
        }
        impact.append(". ").append(facts.alerts().size()).append(" alerts fired: ")
                .append(String.join("; ", facts.alerts())).append('.');

        String rootCause;
        if (facts.likelyCause() != null) {
            rootCause = "%s (from the %s analysis, %d%% confidence). Confirm or correct this before approving."
                    .formatted(facts.likelyCause().text(), facts.likelyCause().analyzer(),
                            Math.round(facts.likelyCause().confidence() * 100));
        } else {
            rootCause = "To be written. The probable origin was " + facts.probableOrigin()
                    + "; no root-cause analysis was requested during the incident.";
        }

        List<String> prevention = new ArrayList<>();
        for (String deployment : facts.deployments()) {
            String resource = deployment.contains(":") ? deployment.substring(0, deployment.indexOf(':')) : deployment;
            if (resource.equals(facts.probableOrigin())) {
                prevention.add("Release " + resource + " with a canary and automatic rollback on error-rate increase.");
            }
        }
        prevention.add("Review the alert thresholds and durations for " + facts.probableOrigin()
                + " so this is detected earlier.");
        prevention.add("Update or write the runbook for this failure with the steps that fixed it.");

        return new PostmortemContent(summary, impact.toString(), rootCause, facts.resolutionNote(), prevention);
    }

    static String minutes(Duration duration) {
        long minutes = Math.max(1, Math.round(duration.toSeconds() / 60.0));
        return minutes + " min";
    }
}
