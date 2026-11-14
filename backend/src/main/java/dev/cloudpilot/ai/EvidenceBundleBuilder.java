package dev.cloudpilot.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.cloudpilot.incidents.IncidentViews;
import dev.cloudpilot.knowledge.RunbookSearch;

/**
 * Turns an incident's data into a numbered, size-limited evidence bundle. Pure: no database,
 * no clock, so what the model sees for a given incident is fully testable.
 */
public class EvidenceBundleBuilder {

    private final int maxDistinctLogs;
    private final int maxRunbookSections;

    public EvidenceBundleBuilder(int maxDistinctLogs, int maxRunbookSections) {
        this.maxDistinctLogs = maxDistinctLogs;
        this.maxRunbookSections = maxRunbookSections;
    }

    public EvidenceBundle build(IncidentViews.Detail incident, List<IncidentViews.TimelineItem> timeline,
                                List<RunbookSearch.Hit> runbookHits, List<String> dependsOn, String question) {
        List<EvidenceItem> items = new ArrayList<>();

        int n = 0;
        for (IncidentViews.AlertItem alert : incident.alerts()) {
            String text = alert.resolvedAt() == null ? alert.message() : alert.message() + " (since resolved)";
            items.add(new EvidenceItem("A" + (++n), EvidenceItem.Kind.ALERT, alert.resourceName(), alert.firedAt(),
                    text, alert.reason()));
        }

        n = 0;
        for (IncidentViews.TimelineItem item : timeline) {
            if (item.type() == IncidentViews.ItemType.DEPLOYMENT) {
                items.add(new EvidenceItem("D" + (++n), EvidenceItem.Kind.DEPLOYMENT, item.resourceName(),
                        item.occurredAt(), item.message(), item.actor()));
            }
        }

        n = 0;
        for (LogGroup group : distinctLogs(timeline)) {
            String text = group.count == 1 ? group.message : group.message + " (seen " + group.count + " times)";
            items.add(new EvidenceItem("L" + (++n), EvidenceItem.Kind.LOG, group.resourceName, group.firstSeen, text,
                    group.level));
        }

        n = 0;
        for (RunbookSearch.Hit hit : runbookHits.stream().limit(maxRunbookSections).toList()) {
            items.add(new EvidenceItem("R" + (++n), EvidenceItem.Kind.RUNBOOK, null, null, hit.content(),
                    hit.heading()));
        }

        List<String> resources = incident.resources().stream().map(IncidentViews.ResourceItem::name).toList();
        String origin = incident.resources().stream().filter(IncidentViews.ResourceItem::probableOrigin)
                .map(IncidentViews.ResourceItem::name).findFirst().orElse(null);

        return new EvidenceBundle(incident.id(), incident.key(), incident.title(), incident.status().name(), question,
                resources, origin, dependsOn, items);
    }

    private static final class LogGroup {
        final String resourceName;
        final String message;
        final String level;
        final Instant firstSeen;
        int count;

        LogGroup(String resourceName, String message, String level, Instant firstSeen) {
            this.resourceName = resourceName;
            this.message = message;
            this.level = level;
            this.firstSeen = firstSeen;
        }
    }

    /**
     * The same error repeats hundreds of times in an incident; the model needs each distinct
     * message once, with how often it appeared. The earliest distinct lines are kept because
     * the onset of a failure says most about its cause.
     */
    private List<LogGroup> distinctLogs(List<IncidentViews.TimelineItem> timeline) {
        Map<String, LogGroup> groups = new LinkedHashMap<>();
        timeline.stream()
                .filter(i -> i.type() == IncidentViews.ItemType.LOG)
                .sorted((a, b) -> a.occurredAt().compareTo(b.occurredAt()))
                .forEach(log -> groups.computeIfAbsent(log.resourceName() + "\u0000" + log.message(),
                        key -> new LogGroup(log.resourceName(), log.message(), log.level(), log.occurredAt())).count++);
        return groups.values().stream().limit(maxDistinctLogs).toList();
    }
}
