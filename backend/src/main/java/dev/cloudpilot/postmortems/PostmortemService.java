package dev.cloudpilot.postmortems;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.cloudpilot.ai.AnalysisService;
import dev.cloudpilot.ai.ClaudeGateway;
import dev.cloudpilot.common.ConflictException;
import dev.cloudpilot.common.NotFoundException;
import dev.cloudpilot.incidents.IncidentQueryService;
import dev.cloudpilot.incidents.IncidentStatus;
import dev.cloudpilot.incidents.IncidentViews;
import dev.cloudpilot.incidents.IncidentViews.ItemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostmortemService {

    private static final Logger log = LoggerFactory.getLogger(PostmortemService.class);

    /** Everything except raw log lines: those belong in the analysis, not in the document. */
    private static final EnumSet<ItemType> TIMELINE_TYPES = EnumSet.of(ItemType.ALERT_FIRED, ItemType.ALERT_RESOLVED,
            ItemType.STATUS_CHANGED, ItemType.ASSIGNED, ItemType.COMMENT, ItemType.DEPLOYMENT);

    private final PostmortemRepository postmortems;
    private final IncidentQueryService incidents;
    private final AnalysisService analyses;
    private final Optional<ClaudePostmortemWriter> claude;
    private final TemplatePostmortemWriter template = new TemplatePostmortemWriter();
    private final Clock clock;

    public PostmortemService(PostmortemRepository postmortems, IncidentQueryService incidents, AnalysisService analyses,
                             ObjectProvider<ClaudeGateway> gateway, Clock clock) {
        this.postmortems = postmortems;
        this.incidents = incidents;
        this.analyses = analyses;
        this.claude = Optional.ofNullable(gateway.getIfAvailable()).map(ClaudePostmortemWriter::new);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PostmortemView get(UUID incidentId) {
        // Look the postmortem up first: "none yet" (404) is the answer for an unresolved incident too.
        Postmortem postmortem = find(incidentId);
        return PostmortemView.of(postmortem, facts(incidentId));
    }

    /** Creates the draft, or replaces it while it is still a draft. */
    @Transactional
    public PostmortemView generate(UUID incidentId, String actor) {
        PostmortemFacts facts = facts(incidentId);
        Optional<Postmortem> existing = postmortems.findByIncidentId(incidentId);
        if (existing.isPresent() && existing.get().getStatus() == Postmortem.Status.APPROVED) {
            throw new ConflictException("This postmortem is approved and can no longer be changed");
        }

        PostmortemContent draft = null;
        String draftedBy = "template";
        String model = null;
        if (claude.isPresent()) {
            try {
                draft = claude.get().write(facts);
                draftedBy = "claude";
                model = claude.get().model();
            } catch (RuntimeException e) {
                log.warn("AI postmortem draft failed for {}, using the template", facts.incidentKey(), e);
            }
        }
        if (draft == null) {
            draft = template.write(facts);
        }

        Instant now = Instant.now(clock);
        Postmortem postmortem;
        if (existing.isPresent()) {
            postmortem = existing.get();
            postmortem.redraft(draft, draftedBy, model, actor, now);
        } else {
            postmortem = postmortems.save(new Postmortem(incidentId, draft, draftedBy, model, actor, now));
        }
        return PostmortemView.of(postmortem, facts);
    }

    @Transactional
    public PostmortemView edit(UUID incidentId, PostmortemContent content, String actor) {
        Postmortem postmortem = find(incidentId);
        postmortem.edit(content, actor, Instant.now(clock));
        return PostmortemView.of(postmortem, facts(incidentId));
    }

    @Transactional
    public PostmortemView approve(UUID incidentId, String actor) {
        Postmortem postmortem = find(incidentId);
        postmortem.approve(actor, Instant.now(clock));
        return PostmortemView.of(postmortem, facts(incidentId));
    }

    private Postmortem find(UUID incidentId) {
        return postmortems.findByIncidentId(incidentId)
                .orElseThrow(() -> new NotFoundException("No postmortem yet for incident " + incidentId));
    }

    private PostmortemFacts facts(UUID incidentId) {
        IncidentViews.Detail incident = incidents.detail(incidentId);
        if (incident.status() != IncidentStatus.RESOLVED) {
            throw new ConflictException("Resolve the incident before writing its postmortem");
        }
        List<IncidentViews.TimelineItem> timeline = incidents.timeline(incidentId, TIMELINE_TYPES, null);

        PostmortemFacts.LikelyCause cause = analyses.findLatest(incidentId)
                .map(a -> new PostmortemFacts.LikelyCause(a.likelyCause().text(), a.confidence(), a.analyzer()))
                .orElse(null);
        String origin = incident.resources().stream().filter(IncidentViews.ResourceItem::probableOrigin)
                .map(IncidentViews.ResourceItem::name).findFirst().orElse("unknown");

        return new PostmortemFacts(incident.key(), incident.title(), incident.severity().name(), incident.openedAt(),
                incident.acknowledgedAt(), incident.resolvedAt(), incident.resolution(),
                incident.resources().stream().map(IncidentViews.ResourceItem::name).toList(), origin,
                incident.alerts().stream().map(IncidentViews.AlertItem::message).toList(), cause,
                timeline.stream().filter(t -> t.type() == ItemType.DEPLOYMENT)
                        .map(t -> t.resourceName() + ": " + t.message()).toList(),
                timeline.stream().filter(t -> t.type() == ItemType.COMMENT).map(IncidentViews.TimelineItem::message).toList(),
                timeline.stream().map(t -> new PostmortemFacts.TimelineLine(t.occurredAt(), t.message())).toList());
    }
}
