package dev.cloudpilot.postmortems;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The recorded facts a postmortem is drafted from. Every number in a draft must come from here.
 *
 * @param likelyCause the latest analysis' likely cause, or null if nobody asked for one
 * @param timeline    recorded events in time order (never written by a model)
 */
public record PostmortemFacts(String incidentKey, String title, String severity, Instant openedAt,
                              Instant acknowledgedAt, Instant resolvedAt, String resolutionNote,
                              List<String> resources, String probableOrigin, List<String> alerts,
                              LikelyCause likelyCause, List<String> deployments, List<String> comments,
                              List<TimelineLine> timeline) {

    public record LikelyCause(String text, double confidence, String analyzer) {
    }

    public record TimelineLine(Instant at, String text) {
    }

    public Duration duration() {
        return Duration.between(openedAt, resolvedAt);
    }

    public Duration timeToAcknowledge() {
        return acknowledgedAt == null ? null : Duration.between(openedAt, acknowledgedAt);
    }
}
