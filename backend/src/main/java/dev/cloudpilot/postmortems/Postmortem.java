package dev.cloudpilot.postmortems;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import dev.cloudpilot.common.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A postmortem: drafted by the system, then owned by people. Approval is the human sign-off
 * (ADR-0002) and makes the document immutable, so the record can't drift after review.
 */
@Entity
@Table(name = "postmortems")
public class Postmortem {

    public enum Status { DRAFT, APPROVED }

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false, unique = true, updatable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String impact;

    @Column(name = "root_cause", nullable = false)
    private String rootCause;

    @Column(nullable = false)
    private String resolution;

    @Column(nullable = false)
    private String prevention;

    @Column(name = "drafted_by", nullable = false)
    private String draftedBy;

    private String model;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Version
    private int version;

    protected Postmortem() {
        // for JPA
    }

    public Postmortem(UUID incidentId, PostmortemContent draft, String draftedBy, String model, String by, Instant at) {
        this.id = UUID.randomUUID();
        this.incidentId = incidentId;
        this.status = Status.DRAFT;
        this.createdBy = by;
        this.createdAt = at;
        apply(draft, by, at);
        this.draftedBy = draftedBy;
        this.model = model;
    }

    public void edit(PostmortemContent content, String by, Instant at) {
        requireDraft();
        apply(content, by, at);
    }

    /** Replace the draft with a freshly generated one (only before approval). */
    public void redraft(PostmortemContent draft, String draftedBy, String model, String by, Instant at) {
        requireDraft();
        apply(draft, by, at);
        this.draftedBy = draftedBy;
        this.model = model;
    }

    public void approve(String by, Instant at) {
        requireDraft();
        if (rootCause.isBlank()) {
            throw new IllegalArgumentException("Write the root cause before approving");
        }
        if (prevention.isBlank()) {
            throw new IllegalArgumentException("Add at least one prevention action before approving");
        }
        this.status = Status.APPROVED;
        this.approvedBy = by;
        this.approvedAt = at;
    }

    public PostmortemContent content() {
        return new PostmortemContent(summary, impact, rootCause, resolution, lines(prevention));
    }

    private void apply(PostmortemContent content, String by, Instant at) {
        this.summary = text(content.summary());
        this.impact = text(content.impact());
        this.rootCause = text(content.rootCause());
        this.resolution = text(content.resolution());
        this.prevention = String.join("\n", content.prevention() == null ? List.of()
                : content.prevention().stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
        this.updatedBy = by;
        this.updatedAt = at;
    }

    private void requireDraft() {
        if (status != Status.DRAFT) {
            throw new ConflictException("This postmortem is approved and can no longer be changed");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> lines(String value) {
        return value.isBlank() ? List.of() : Arrays.stream(value.split("\n")).toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public Status getStatus() {
        return status;
    }

    public String getDraftedBy() {
        return draftedBy;
    }

    public String getModel() {
        return model;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
