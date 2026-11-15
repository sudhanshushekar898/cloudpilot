package dev.cloudpilot.reports;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reliability report for a time window. Averages are null when there is nothing to average,
 * so the UI can show "no data" instead of a misleading zero.
 */
public record Report(Instant from, Instant to, long opened, long resolved, long active,
                     Double mttaMinutes, Double mttrMinutes, Double slaCompliancePercent, List<Sla> sla,
                     Map<String, Long> bySeverity, List<Recurring> recurring, List<ResourceCount> topResources,
                     List<Responder> responders) {

    /** Resolution SLA for one severity: how many resolved incidents met the target. */
    public record Sla(String severity, long targetMinutes, long resolved, long withinTarget) {
    }

    /** The same kind of failure on the same resource more than once. */
    public record Recurring(String resource, String signature, long occurrences, Instant lastSeen) {
    }

    public record ResourceCount(String resource, long incidents) {
    }

    public record Responder(String name, long resolved, double mttrMinutes) {
    }
}
