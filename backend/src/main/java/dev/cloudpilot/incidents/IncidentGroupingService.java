package dev.cloudpilot.incidents;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.cloudpilot.alerts.AlertFired;
import dev.cloudpilot.alerts.AlertResolved;
import dev.cloudpilot.resources.ResourceRef;
import dev.cloudpilot.resources.ResourceService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to alert events and keeps incidents up to date.
 *
 * Runs AFTER the alert engine's transaction commits, in its own transaction: a bug here can
 * never roll back or block alert creation. The trade-off is that a crash between the two
 * transactions would lose the grouping step; a transactional outbox would close that gap.
 */
@Service
public class IncidentGroupingService {

    private static final Logger log = LoggerFactory.getLogger(IncidentGroupingService.class);

    static final Duration GROUPING_WINDOW = Duration.ofMinutes(10);
    private static final Set<IncidentStatus> ACTIVE =
            EnumSet.of(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING);

    private final IncidentRepository incidents;
    private final IncidentAlertRepository incidentAlerts;
    private final TimelineEntryRepository timeline;
    private final ResourceService resources;
    private final MeterRegistry registry;
    private final AlertGrouper grouper = new AlertGrouper(GROUPING_WINDOW);

    public IncidentGroupingService(IncidentRepository incidents, IncidentAlertRepository incidentAlerts,
                                   TimelineEntryRepository timeline, ResourceService resources,
                                   MeterRegistry registry) {
        this.incidents = incidents;
        this.incidentAlerts = incidentAlerts;
        this.timeline = timeline;
        this.resources = resources;
        this.registry = registry;
        // Read on every scrape: one cheap indexed count, always current.
        Gauge.builder("cloudpilot.incidents.active", incidents, repo -> repo.countByStatusIn(ACTIVE))
                .description("Incidents not yet resolved")
                .strongReference(true)
                .register(registry);
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlertFired(AlertFired event) {
        // Listeners can be re-run (retries, redelivery); seeing the same alert twice must be harmless.
        if (incidentAlerts.findByAlertId(event.alertId()).isPresent()) {
            return;
        }
        Map<UUID, ResourceRef> topology = resources.topology();
        Map<UUID, Set<UUID>> dependsOn = topology.values().stream()
                .collect(Collectors.toMap(ResourceRef::id, ResourceRef::dependsOn));
        Map<UUID, String> names = topology.values().stream()
                .collect(Collectors.toMap(ResourceRef::id, ResourceRef::name));

        List<Incident> active = incidents.findByStatusInOrderByOpenedAtDesc(ACTIVE);
        Map<UUID, Set<UUID>> resourcesByIncident = incidentAlerts
                .findByIncidentIdIn(active.stream().map(Incident::getId).toList()).stream()
                .collect(Collectors.groupingBy(IncidentAlert::getIncidentId,
                        Collectors.mapping(IncidentAlert::getResourceId, Collectors.toSet())));
        List<AlertGrouper.Candidate> candidates = active.stream()
                .map(i -> new AlertGrouper.Candidate(i.getId(), i.getNumber(),
                        resourcesByIncident.getOrDefault(i.getId(), Set.of()), i.getLastAlertAt()))
                .toList();

        Optional<AlertGrouper.Match> match =
                grouper.match(event.resourceId(), event.firedAt(), candidates, dependsOn, names);

        Incident incident;
        String reason;
        if (match.isPresent()) {
            incident = incidents.findById(match.get().incidentId()).orElseThrow();
            incident.recordAlert(event.severity(), event.firedAt());
            reason = match.get().reason();
            // How much noise grouping removes: each of these would otherwise have been a separate page.
            registry.counter("cloudpilot.alerts.grouped").increment();
        } else {
            incident = incidents.saveAndFlush(new Incident(event.message(), event.severity(), event.firedAt()));
            reason = "First alert";
            log.info("Opened incident for: {}", event.message());
            registry.counter("cloudpilot.incidents.opened", "severity", event.severity().name()).increment();
        }

        incidentAlerts.save(new IncidentAlert(incident.getId(), event.alertId(), event.resourceId(), event.severity(),
                event.message(), reason, event.firedAt()));
        timeline.save(new TimelineEntry(incident.getId(), TimelineEntry.Type.ALERT_FIRED, event.firedAt(), null,
                event.resourceId(), "Alert fired: " + event.message() + " (" + reason + ")"));
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlertResolved(AlertResolved event) {
        incidentAlerts.findByAlertId(event.alertId()).ifPresent(alert -> {
            if (alert.getResolvedAt() != null) {
                return;
            }
            alert.markResolved(event.resolvedAt());
            // The incident stays open: alerts going quiet doesn't mean someone verified the fix.
            timeline.save(new TimelineEntry(alert.getIncidentId(), TimelineEntry.Type.ALERT_RESOLVED,
                    event.resolvedAt(), null, alert.getResourceId(), "Alert resolved: " + alert.getMessage()));
        });
    }
}
