package dev.cloudpilot.reports;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.cloudpilot.incidents.IncidentQueryService;
import dev.cloudpilot.incidents.IncidentViews;
import org.springframework.stereotype.Service;

/**
 * Builds reports from incident summaries. At this scale (hundreds of incidents) computing in
 * Java is simple and fast; with many thousands, the aggregates would move into SQL.
 */
@Service
public class ReportService {

    private final IncidentQueryService incidents;
    private final Clock clock;
    private final ReportCalculator calculator = new ReportCalculator(ReportCalculator.DEFAULT_SLA);

    public ReportService(IncidentQueryService incidents, Clock clock) {
        this.incidents = incidents;
        this.clock = clock;
    }

    public Report summary(int days) {
        Instant to = Instant.now(clock);
        Instant from = to.minus(Duration.ofDays(days));
        List<ReportCalculator.IncidentRecord> records = incidents.list(IncidentQueryService.Scope.ALL).stream()
                .map(ReportService::toRecord)
                .toList();
        return calculator.calculate(records, from, to.plusSeconds(1));
    }

    /** The first resource to alert and its first alert (the incident title) identify the failure. */
    private static ReportCalculator.IncidentRecord toRecord(IncidentViews.Summary s) {
        String origin = s.resourceNames().isEmpty() ? "unknown" : s.resourceNames().getFirst();
        return new ReportCalculator.IncidentRecord(s.key(), s.severity().name(), s.openedAt(), s.acknowledgedAt(),
                s.resolvedAt(), s.assigneeName(), origin, s.title());
    }
}
