package dev.cloudpilot.features;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "feature_flags")
class FeatureFlag {

    @Id
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private String description;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeatureFlag() {
    }

    void set(boolean enabled, String actor, Instant now) {
        this.enabled = enabled;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    String getName() {
        return name;
    }

    boolean isEnabled() {
        return enabled;
    }

    String getDescription() {
        return description;
    }

    String getUpdatedBy() {
        return updatedBy;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
