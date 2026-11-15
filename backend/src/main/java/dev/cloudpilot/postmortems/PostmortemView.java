package dev.cloudpilot.postmortems;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A postmortem as shown: the editable content plus the fact-based timeline and its history. */
public record PostmortemView(UUID id, UUID incidentId, String incidentKey, String incidentTitle, Postmortem.Status status,
                             String summary, String impact, String rootCause, String resolution, List<String> prevention,
                             List<PostmortemFacts.TimelineLine> timeline, String draftedBy, String model,
                             String createdBy, Instant createdAt, String updatedBy, Instant updatedAt,
                             String approvedBy, Instant approvedAt) {

    static PostmortemView of(Postmortem p, PostmortemFacts facts) {
        PostmortemContent c = p.content();
        return new PostmortemView(p.getId(), p.getIncidentId(), facts.incidentKey(), facts.title(), p.getStatus(),
                c.summary(), c.impact(), c.rootCause(), c.resolution(), c.prevention(), facts.timeline(),
                p.getDraftedBy(), p.getModel(), p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt(),
                p.getApprovedBy(), p.getApprovedAt());
    }
}
