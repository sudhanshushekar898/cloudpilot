package dev.cloudpilot.incidents;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import dev.cloudpilot.alerts.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * An alert as seen by its incident: a copy of what we need (resource, message, severity)
 * plus why it was grouped. Copying avoids a dependency from incidents on the alerts tables.
 */
@Entity
@Table(name = "incident_alerts")
@IdClass(IncidentAlert.Key.class)
public class IncidentAlert {

    public record Key(UUID incidentId, UUID alertId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "incident_id")
    private UUID incidentId;

    @Id
    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private String reason;

    @Column(name = "fired_at", nullable = false)
    private Instant firedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected IncidentAlert() {
        // for JPA
    }

    public IncidentAlert(UUID incidentId, UUID alertId, UUID resourceId, Severity severity, String message,
                         String reason, Instant firedAt) {
        this.incidentId = incidentId;
        this.alertId = alertId;
        this.resourceId = resourceId;
        this.severity = severity;
        this.message = message;
        this.reason = reason;
        this.firedAt = firedAt;
    }

    public void markResolved(Instant at) {
        if (resolvedAt == null) {
            resolvedAt = at;
        }
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getReason() {
        return reason;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
