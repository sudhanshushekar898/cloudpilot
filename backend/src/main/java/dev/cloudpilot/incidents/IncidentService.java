package dev.cloudpilot.incidents;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import dev.cloudpilot.auth.Role;
import dev.cloudpilot.auth.UserDirectory;
import dev.cloudpilot.common.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commands people run on an incident. Each one changes the aggregate and appends a
 * timeline entry in the same transaction, so the timeline can never disagree with the state.
 */
@Service
@Transactional
public class IncidentService {

    /** Who is acting: taken from the JWT by the controller. */
    public record Actor(UUID userId, String email) {
    }

    private final IncidentRepository incidents;
    private final TimelineEntryRepository timeline;
    private final UserDirectory users;
    private final Clock clock;
    private final MeterRegistry registry;

    public IncidentService(IncidentRepository incidents, TimelineEntryRepository timeline, UserDirectory users,
                           Clock clock, MeterRegistry registry) {
        this.incidents = incidents;
        this.timeline = timeline;
        this.users = users;
        this.clock = clock;
        this.registry = registry;
    }

    public void acknowledge(UUID id, Actor actor) {
        changeStatus(id, actor, incident -> incident.acknowledge(actor.userId(), now()));
    }

    public void startInvestigating(UUID id, Actor actor) {
        changeStatus(id, actor, incident -> incident.startInvestigating(now()));
    }

    public void resolve(UUID id, Actor actor, String resolution) {
        changeStatus(id, actor, incident -> {
            incident.resolve(resolution, now());
            // MTTR as a live metric, per severity; the Reports page computes the same from the database.
            Timer.builder("cloudpilot.incidents.time.to.resolve")
                    .tag("severity", incident.getSeverity().name())
                    .register(registry)
                    .record(Duration.between(incident.getOpenedAt(), incident.getResolvedAt()));
        });
    }

    public void assign(UUID id, Actor actor, UUID assigneeId) {
        Incident incident = find(id);
        UserDirectory.Person assignee = users.find(assigneeId)
                .filter(p -> p.role() == Role.SRE || p.role() == Role.ADMIN)
                .orElseThrow(() -> new NotFoundException("No SRE or admin with id " + assigneeId));
        incident.assign(assignee.id());
        timeline.save(new TimelineEntry(id, TimelineEntry.Type.ASSIGNED, now(), actor.email(), null,
                "Assigned to " + assignee.displayName()));
    }

    public void comment(UUID id, Actor actor, String text) {
        find(id);
        timeline.save(new TimelineEntry(id, TimelineEntry.Type.COMMENT, now(), actor.email(), null, text.trim()));
    }

    private void changeStatus(UUID id, Actor actor, Consumer<Incident> change) {
        Incident incident = find(id);
        IncidentStatus before = incident.getStatus();
        change.accept(incident);
        timeline.save(new TimelineEntry(id, TimelineEntry.Type.STATUS_CHANGED, now(), actor.email(), null,
                "Status changed from " + before + " to " + incident.getStatus()));
    }

    private Incident find(UUID id) {
        return incidents.findById(id).orElseThrow(() -> new NotFoundException("No incident with id " + id));
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
