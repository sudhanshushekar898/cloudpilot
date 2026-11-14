package dev.cloudpilot.incidents;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Decides whether a new alert belongs to an existing incident (noise reduction).
 *
 * An alert joins an active incident when, within the grouping window, the incident already
 * has alerts on the same resource, on a resource this one depends on, or on a resource that
 * depends on this one. Because the incident's resource set grows as alerts join, a failure
 * that ripples through a chain (db -> api -> frontend) ends up as one incident.
 *
 * Every match carries a human-readable reason, shown in the UI, so grouping is never a mystery.
 */
public class AlertGrouper {

    private final Duration window;

    public AlertGrouper(Duration window) {
        this.window = window;
    }

    /** An active incident that could absorb the alert. */
    public record Candidate(UUID incidentId, long number, Set<UUID> resourceIds, Instant lastAlertAt) {
    }

    public record Match(UUID incidentId, String reason) {
    }

    public Optional<Match> match(UUID resourceId, Instant firedAt, Collection<Candidate> candidates,
                                 Map<UUID, Set<UUID>> dependsOn, Map<UUID, String> names) {
        return candidates.stream()
                .filter(c -> !firedAt.isAfter(c.lastAlertAt().plus(window)))
                .sorted(Comparator.comparing(Candidate::lastAlertAt).reversed())
                .map(c -> reason(resourceId, c, dependsOn, names).map(reason -> new Match(c.incidentId(), reason)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> reason(UUID resourceId, Candidate candidate, Map<UUID, Set<UUID>> dependsOn,
                                           Map<UUID, String> names) {
        String incident = Incident.displayNumber(candidate.number());
        String name = names.getOrDefault(resourceId, resourceId.toString());

        if (candidate.resourceIds().contains(resourceId)) {
            return Optional.of(name + " already has alerts in " + incident);
        }
        Set<UUID> myDependencies = dependsOn.getOrDefault(resourceId, Set.of());
        for (UUID other : candidate.resourceIds()) {
            String otherName = names.getOrDefault(other, other.toString());
            if (myDependencies.contains(other)) {
                return Optional.of(name + " depends on " + otherName + ", already in " + incident);
            }
            if (dependsOn.getOrDefault(other, Set.of()).contains(resourceId)) {
                return Optional.of(otherName + " (in " + incident + ") depends on " + name);
            }
        }
        return Optional.empty();
    }
}
