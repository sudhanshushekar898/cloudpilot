package dev.cloudpilot.incidents;

import java.time.Instant;
import java.util.UUID;

import dev.cloudpilot.alerts.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;

/**
 * Aggregate root for an incident. All state changes go through its methods, which enforce
 * the lifecycle, so no service can put an incident into an impossible state.
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    private UUID id;

    /** Assigned by the database sequence; shown to people as INC-42. */
    @Generated
    @Column(insertable = false, updatable = false)
    private Long number;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "investigating_at")
    private Instant investigatingAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "last_alert_at", nullable = false)
    private Instant lastAlertAt;

    private String resolution;

    /** Two people acknowledging at once: the second save fails instead of silently overwriting. */
    @Version
    private int version;

    protected Incident() {
        // for JPA
    }

    public Incident(String title, Severity severity, Instant openedAt) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.status = IncidentStatus.OPEN;
        this.severity = severity;
        this.openedAt = openedAt;
        this.lastAlertAt = openedAt;
    }

    public static String displayNumber(long number) {
        return "INC-" + number;
    }

    public void acknowledge(UUID actorId, Instant at) {
        moveTo(IncidentStatus.ACKNOWLEDGED);
        acknowledgedAt = at;
        if (assigneeId == null) {
            assigneeId = actorId;
        }
    }

    public void startInvestigating(Instant at) {
        moveTo(IncidentStatus.INVESTIGATING);
        investigatingAt = at;
    }

    public void resolve(String resolutionNote, Instant at) {
        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new IllegalArgumentException("A resolution note is required");
        }
        moveTo(IncidentStatus.RESOLVED);
        resolution = resolutionNote.trim();
        resolvedAt = at;
    }

    public void assign(UUID userId) {
        requireActive();
        assigneeId = userId;
    }

    /** A new alert joined: keep the worst severity and remember when it happened. */
    public void recordAlert(Severity alertSeverity, Instant at) {
        requireActive();
        if (alertSeverity.ordinal() < severity.ordinal()) {
            severity = alertSeverity;
        }
        if (at.isAfter(lastAlertAt)) {
            lastAlertAt = at;
        }
    }

    private void moveTo(IncidentStatus next) {
        if (!status.canMoveTo(next)) {
            throw new InvalidTransitionException("Cannot move incident from " + status + " to " + next);
        }
        status = next;
    }

    private void requireActive() {
        if (!status.isActive()) {
            throw new InvalidTransitionException("Incident is already resolved");
        }
    }

    public UUID getId() {
        return id;
    }

    public Long getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Severity getSeverity() {
        return severity;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant getInvestigatingAt() {
        return investigatingAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getLastAlertAt() {
        return lastAlertAt;
    }

    public String getResolution() {
        return resolution;
    }
}
