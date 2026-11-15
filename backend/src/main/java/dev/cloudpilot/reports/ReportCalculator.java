package dev.cloudpilot.reports;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Computes reliability metrics from incident records. Pure, so every definition below is pinned
 * down by a unit test:
 * <ul>
 *   <li><b>Window:</b> incidents opened in [from, to). MTTR and SLA use the resolved ones among them.</li>
 *   <li><b>MTTA:</b> mean of acknowledged - opened, over acknowledged incidents.</li>
 *   <li><b>MTTR:</b> mean of resolved - opened, over resolved incidents (detection to recovery
 *       as recorded; time before the first alert is not visible here).</li>
 *   <li><b>SLA:</b> share of resolved incidents fixed within their severity's target.</li>
 *   <li><b>Recurring:</b> same origin resource and same first-alert text once numbers are removed,
 *       at least twice in the window.</li>
 * </ul>
 */
public class ReportCalculator {

    /** Resolution targets per severity. */
    public static final Map<String, Duration> DEFAULT_SLA = Map.of(
            "CRITICAL", Duration.ofMinutes(60),
            "WARNING", Duration.ofHours(4),
            "INFO", Duration.ofHours(24));

    private final Map<String, Duration> slaTargets;

    public ReportCalculator(Map<String, Duration> slaTargets) {
        this.slaTargets = slaTargets;
    }

    /** What the calculator needs to know about one incident. */
    public record IncidentRecord(String key, String severity, Instant openedAt, Instant acknowledgedAt,
                                 Instant resolvedAt, String assignee, String origin, String firstAlert) {
    }

    public Report calculate(List<IncidentRecord> all, Instant from, Instant to) {
        List<IncidentRecord> inWindow = all.stream()
                .filter(i -> !i.openedAt().isBefore(from) && i.openedAt().isBefore(to))
                .toList();
        List<IncidentRecord> resolved = inWindow.stream().filter(i -> i.resolvedAt() != null).toList();

        Double mtta = average(inWindow.stream().filter(i -> i.acknowledgedAt() != null)
                .mapToDouble(i -> minutes(i.openedAt(), i.acknowledgedAt())).average());
        Double mttr = average(resolved.stream().mapToDouble(i -> minutes(i.openedAt(), i.resolvedAt())).average());

        List<Report.Sla> sla = slaTargets.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(target -> {
                    List<IncidentRecord> ofSeverity = resolved.stream()
                            .filter(i -> i.severity().equals(target.getKey())).toList();
                    long within = ofSeverity.stream()
                            .filter(i -> !Duration.between(i.openedAt(), i.resolvedAt()).minus(target.getValue()).isPositive())
                            .count();
                    return new Report.Sla(target.getKey(), target.getValue().toMinutes(), ofSeverity.size(), within);
                })
                .toList();
        long withinTotal = sla.stream().mapToLong(Report.Sla::withinTarget).sum();
        long resolvedWithTarget = sla.stream().mapToLong(Report.Sla::resolved).sum();
        Double compliance = resolvedWithTarget == 0 ? null : 100.0 * withinTotal / resolvedWithTarget;

        Map<String, Long> bySeverity = inWindow.stream()
                .collect(Collectors.groupingBy(IncidentRecord::severity, TreeMap::new, Collectors.counting()));

        return new Report(from, to, inWindow.size(), resolved.size(), inWindow.size() - resolved.size(), mtta, mttr,
                compliance, sla, bySeverity, recurring(inWindow), topResources(inWindow), responders(resolved));
    }

    private static List<Report.Recurring> recurring(List<IncidentRecord> incidents) {
        Map<String, List<IncidentRecord>> groups = incidents.stream()
                .collect(Collectors.groupingBy(i -> i.origin() + "\u0000" + signature(i.firstAlert()),
                        LinkedHashMap::new, Collectors.toList()));
        return groups.values().stream()
                .filter(group -> group.size() >= 2)
                .map(group -> new Report.Recurring(group.getFirst().origin(), signature(group.getFirst().firstAlert()),
                        group.size(), group.stream().map(IncidentRecord::openedAt).max(Comparator.naturalOrder()).orElseThrow()))
                .sorted(Comparator.comparingLong(Report.Recurring::occurrences).reversed()
                        .thenComparing(Report.Recurring::lastSeen, Comparator.reverseOrder()))
                .toList();
    }

    /** "orders-db cpu 97.5% > 90% for 3 min" -> "orders-db cpu #% > #% for # min". */
    static String signature(String alert) {
        return alert == null ? "" : alert.replaceAll("\\d+(\\.\\d+)?", "#");
    }

    private static List<Report.ResourceCount> topResources(List<IncidentRecord> incidents) {
        return incidents.stream()
                .collect(Collectors.groupingBy(IncidentRecord::origin, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new Report.ResourceCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(Report.ResourceCount::incidents).reversed()
                        .thenComparing(Report.ResourceCount::resource))
                .limit(5)
                .toList();
    }

    private static List<Report.Responder> responders(List<IncidentRecord> resolved) {
        return resolved.stream()
                .filter(i -> i.assignee() != null)
                .collect(Collectors.groupingBy(IncidentRecord::assignee, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new Report.Responder(e.getKey(), e.getValue().size(),
                        e.getValue().stream().mapToDouble(i -> minutes(i.openedAt(), i.resolvedAt())).average().orElse(0)))
                .sorted(Comparator.comparingLong(Report.Responder::resolved).reversed()
                        .thenComparing(Report.Responder::name))
                .toList();
    }

    private static double minutes(Instant from, Instant to) {
        return Duration.between(from, to).toMillis() / 60_000.0;
    }

    private static Double average(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }
}
