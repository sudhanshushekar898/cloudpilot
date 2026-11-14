package dev.cloudpilot.incidents;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.cloudpilot.auth.UserDirectory;
import dev.cloudpilot.common.NotFoundException;
import dev.cloudpilot.metrics.EventStore;
import dev.cloudpilot.resources.ResourceRef;
import dev.cloudpilot.resources.ResourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.cloudpilot.incidents.IncidentViews.ItemType;

@Service
@Transactional(readOnly = true)
public class IncidentQueryService {

    public enum Scope { ACTIVE, RESOLVED, ALL }

    /** Logs from shortly before the first alert often show the real start of the problem. */
    static final Duration LOGS_BEFORE = Duration.ofMinutes(15);
    /** A deployment an hour before is still a prime suspect. */
    static final Duration DEPLOYMENTS_BEFORE = Duration.ofHours(1);
    static final int MAX_LOG_LINES = 200;

    private final IncidentRepository incidents;
    private final IncidentAlertRepository incidentAlerts;
    private final TimelineEntryRepository timeline;
    private final ResourceService resources;
    private final UserDirectory users;
    private final EventStore events;
    private final Clock clock;

    public IncidentQueryService(IncidentRepository incidents, IncidentAlertRepository incidentAlerts,
                                TimelineEntryRepository timeline, ResourceService resources, UserDirectory users,
                                EventStore events, Clock clock) {
        this.incidents = incidents;
        this.incidentAlerts = incidentAlerts;
        this.timeline = timeline;
        this.resources = resources;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    public List<IncidentViews.Summary> list(Scope scope) {
        List<Incident> found = switch (scope) {
            case ACTIVE -> incidents.findByStatusInOrderByOpenedAtDesc(
                    EnumSet.of(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING));
            case RESOLVED -> incidents.findByStatusInOrderByOpenedAtDesc(EnumSet.of(IncidentStatus.RESOLVED));
            case ALL -> incidents.findAllByOrderByOpenedAtDesc();
        };
        Map<UUID, List<IncidentAlert>> alertsByIncident = incidentAlerts
                .findByIncidentIdIn(found.stream().map(Incident::getId).toList()).stream()
                .collect(Collectors.groupingBy(IncidentAlert::getIncidentId));
        Map<UUID, ResourceRef> topology = resources.topology();
        Map<UUID, UserDirectory.Person> assignees = users.findAll(
                found.stream().map(Incident::getAssigneeId).filter(Objects::nonNull).collect(Collectors.toSet()));

        return found.stream().map(incident -> {
            List<IncidentAlert> alerts = alertsByIncident.getOrDefault(incident.getId(), List.of());
            UserDirectory.Person assignee = incident.getAssigneeId() == null ? null : assignees.get(incident.getAssigneeId());
            List<String> resourceNames = alerts.stream().map(a -> name(topology, a.getResourceId())).distinct().toList();
            return new IncidentViews.Summary(incident.getId(), Incident.displayNumber(incident.getNumber()),
                    incident.getTitle(), incident.getStatus(), incident.getSeverity(),
                    assignee == null ? null : assignee.displayName(), alerts.size(),
                    (int) alerts.stream().filter(a -> a.getResolvedAt() == null).count(), resourceNames,
                    incident.getOpenedAt(), incident.getResolvedAt());
        }).toList();
    }

    public long activeCount() {
        return incidents.countByStatusIn(
                EnumSet.of(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING));
    }

    public IncidentViews.Detail detail(UUID id) {
        Incident incident = find(id);
        List<IncidentAlert> alerts = incidentAlerts.findByIncidentIdOrderByFiredAtAsc(id);
        Map<UUID, ResourceRef> topology = resources.topology();

        IncidentViews.Person assignee = incident.getAssigneeId() == null ? null : users.find(incident.getAssigneeId())
                .map(p -> new IncidentViews.Person(p.id(), p.displayName())).orElse(null);

        List<IncidentViews.AlertItem> alertItems = alerts.stream().map(a -> new IncidentViews.AlertItem(
                a.getAlertId(), a.getResourceId(), name(topology, a.getResourceId()), a.getSeverity(), a.getMessage(),
                a.getReason(), a.getFiredAt(), a.getResolvedAt())).toList();

        return new IncidentViews.Detail(incident.getId(), Incident.displayNumber(incident.getNumber()),
                incident.getTitle(), incident.getStatus(), incident.getSeverity(), assignee, incident.getOpenedAt(),
                incident.getAcknowledgedAt(), incident.getInvestigatingAt(), incident.getResolvedAt(),
                incident.getResolution(), !alerts.isEmpty() && alerts.stream().allMatch(a -> a.getResolvedAt() != null),
                alertItems, resourceItems(alerts, topology));
    }

    /**
     * Everything relevant to the incident in time order: our own entries (alerts, status, comments)
     * plus logs and deployments of the involved resources around the incident window.
     */
    public List<IncidentViews.TimelineItem> timeline(UUID id, Set<ItemType> types, UUID resourceId) {
        Incident incident = find(id);
        Map<UUID, ResourceRef> topology = resources.topology();
        Set<UUID> involved = incidentAlerts.findByIncidentIdOrderByFiredAtAsc(id).stream()
                .map(IncidentAlert::getResourceId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (resourceId != null) {
            involved.retainAll(Set.of(resourceId));
        }
        Instant end = incident.getResolvedAt() != null
                ? incident.getResolvedAt().plus(Duration.ofMinutes(5))
                : Instant.now(clock).plusSeconds(1);

        List<IncidentViews.TimelineItem> items = new ArrayList<>();
        for (TimelineEntry entry : timeline.findByIncidentIdOrderByOccurredAtAsc(id)) {
            items.add(new IncidentViews.TimelineItem(ItemType.valueOf(entry.getType().name()), entry.getOccurredAt(),
                    entry.getMessage(), entry.getActor(), entry.getResourceId(),
                    entry.getResourceId() == null ? null : name(topology, entry.getResourceId()), null));
        }
        events.logs(involved, incident.getOpenedAt().minus(LOGS_BEFORE), end, MAX_LOG_LINES).forEach(log ->
                items.add(new IncidentViews.TimelineItem(ItemType.LOG, log.loggedAt(), log.message(), null,
                        log.resourceId(), name(topology, log.resourceId()), log.level().name())));
        // A release to something an affected service calls is a prime suspect too.
        Set<UUID> deploymentScope = new LinkedHashSet<>(involved);
        if (resourceId == null) {
            involved.forEach(r -> deploymentScope.addAll(
                    topology.containsKey(r) ? topology.get(r).dependsOn() : Set.of()));
        }
        events.deployments(deploymentScope, incident.getOpenedAt().minus(DEPLOYMENTS_BEFORE), end).forEach(d ->
                items.add(new IncidentViews.TimelineItem(ItemType.DEPLOYMENT, d.deployedAt(),
                        "Deployed " + d.version() + ": " + d.description(), d.deployedBy(), d.resourceId(),
                        name(topology, d.resourceId()), null)));

        return items.stream()
                .filter(item -> types.isEmpty() || types.contains(item.type()))
                .filter(item -> resourceId == null || resourceId.equals(item.resourceId()))
                .sorted(Comparator.comparing(IncidentViews.TimelineItem::occurredAt))
                .toList();
    }

    private static List<IncidentViews.ResourceItem> resourceItems(List<IncidentAlert> alerts, Map<UUID, ResourceRef> topology) {
        // Resources in the order they first alerted.
        List<UUID> involved = alerts.stream().map(IncidentAlert::getResourceId).distinct().toList();
        Set<UUID> involvedSet = Set.copyOf(involved);
        UUID origin = involved.stream()
                .filter(r -> topology.containsKey(r)
                        && topology.get(r).dependsOn().stream().noneMatch(involvedSet::contains))
                .findFirst().orElse(null);
        return involved.stream().filter(topology::containsKey).map(r -> {
            ResourceRef ref = topology.get(r);
            return new IncidentViews.ResourceItem(r, ref.name(), ref.type(), r.equals(origin));
        }).toList();
    }

    private static String name(Map<UUID, ResourceRef> topology, UUID id) {
        ResourceRef ref = topology.get(id);
        return ref == null ? "(unknown resource)" : ref.name();
    }

    private Incident find(UUID id) {
        return incidents.findById(id).orElseThrow(() -> new NotFoundException("No incident with id " + id));
    }
}
