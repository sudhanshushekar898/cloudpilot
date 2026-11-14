package dev.cloudpilot.alerts;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.cloudpilot.metrics.MetricType;
import dev.cloudpilot.resources.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetricType metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operator operator;

    @Column(nullable = false)
    private double threshold;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    /** Null means the rule applies to every resource type. */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type")
    private ResourceType resourceType;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AlertRule() {
        // for JPA
    }

    public AlertRule(String name, MetricType metric, Operator operator, double threshold, Duration duration,
                     Severity severity, ResourceType resourceType, Instant now) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        update(name, metric, operator, threshold, duration, severity, resourceType, true, now);
    }

    public void update(String name, MetricType metric, Operator operator, double threshold, Duration duration,
                       Severity severity, ResourceType resourceType, boolean enabled, Instant now) {
        this.name = name;
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.durationSeconds = Math.toIntExact(duration.toSeconds());
        this.severity = severity;
        this.resourceType = resourceType;
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public boolean appliesTo(ResourceType type) {
        return resourceType == null || resourceType == type;
    }

    public RuleCondition condition() {
        return new RuleCondition(operator, threshold, getDuration());
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public MetricType getMetric() {
        return metric;
    }

    public Operator getOperator() {
        return operator;
    }

    public double getThreshold() {
        return threshold;
    }

    public Duration getDuration() {
        return Duration.ofSeconds(durationSeconds);
    }

    public Severity getSeverity() {
        return severity;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
