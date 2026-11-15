package dev.cloudpilot.incidents;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.cloudpilot.alerts.Severity;
import dev.cloudpilot.resources.ResourceType;

/** Read models returned by the incidents API. */
public final class IncidentViews {

    private IncidentViews() {
    }

    public record Summary(UUID id, String key, String title, IncidentStatus status, Severity severity,
                          String assigneeName, int alertCount, int activeAlertCount, List<String> resourceNames,
                          Instant openedAt, Instant acknowledgedAt, Instant resolvedAt) {
    }

    public record Detail(UUID id, String key, String title, IncidentStatus status, Severity severity,
                         Person assignee, Instant openedAt, Instant acknowledgedAt, Instant investigatingAt,
                         Instant resolvedAt, String resolution, boolean allAlertsResolved,
                         List<AlertItem> alerts, List<ResourceItem> resources) {
    }

    public record Person(UUID id, String displayName) {
    }

    public record AlertItem(UUID alertId, UUID resourceId, String resourceName, Severity severity, String message,
                            String reason, Instant firedAt, Instant resolvedAt) {
    }

    /**
     * A resource involved in the incident. {@code probableOrigin} marks the most upstream one:
     * it has alerts but depends on nothing else in the incident. A hint, not a verdict.
     */
    public record ResourceItem(UUID id, String name, ResourceType type, boolean probableOrigin) {
    }

    public enum ItemType { ALERT_FIRED, ALERT_RESOLVED, STATUS_CHANGED, ASSIGNED, COMMENT, LOG, DEPLOYMENT }

    public record TimelineItem(ItemType type, Instant occurredAt, String message, String actor, UUID resourceId,
                               String resourceName, String level) {
    }
}
