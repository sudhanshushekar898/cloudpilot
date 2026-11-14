package dev.cloudpilot.alerts;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One occurrence of a rule breaching on one resource, from firing until it resolves. */
@Entity
@Table(name = "alerts")
public class Alert {

    public enum Status { FIRING, RESOLVED }

    @Id
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** Copied from the rule when firing, so editing the rule later doesn't rewrite history. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String message;

    @Column(name = "observed_value", nullable = false)
    private double observedValue;

    @Column(name = "fired_at", nullable = false)
    private Instant firedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Alert() {
        // for JPA
    }

    public Alert(UUID ruleId, UUID resourceId, Severity severity, String message, double observedValue, Instant firedAt) {
        this.id = UUID.randomUUID();
        this.ruleId = ruleId;
        this.resourceId = resourceId;
        this.status = Status.FIRING;
        this.severity = severity;
        this.message = message;
        this.observedValue = observedValue;
        this.firedAt = firedAt;
    }

    public void resolve(Instant at) {
        if (status == Status.FIRING) {
            this.status = Status.RESOLVED;
            this.resolvedAt = at;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Status getStatus() {
        return status;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public double getObservedValue() {
        return observedValue;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
