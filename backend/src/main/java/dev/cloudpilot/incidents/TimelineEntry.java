package dev.cloudpilot.incidents;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Append-only record of something that happened during an incident. Never updated. */
@Entity
@Table(name = "timeline_entries")
public class TimelineEntry {

    public enum Type { ALERT_FIRED, ALERT_RESOLVED, STATUS_CHANGED, ASSIGNED, COMMENT }

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Email of the person, or null when the system did it (alerts). */
    @Column(updatable = false)
    private String actor;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(nullable = false, updatable = false)
    private String message;

    protected TimelineEntry() {
        // for JPA
    }

    public TimelineEntry(UUID incidentId, Type type, Instant occurredAt, String actor, UUID resourceId, String message) {
        this.id = UUID.randomUUID();
        this.incidentId = incidentId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.resourceId = resourceId;
        this.message = message;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public Type getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getMessage() {
        return message;
    }
}
