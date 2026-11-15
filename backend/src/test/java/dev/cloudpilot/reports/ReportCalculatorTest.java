package dev.cloudpilot.reports;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReportCalculatorTest {

    private static final Instant TO = Instant.parse("2026-11-15T12:00:00Z");
    private static final Instant FROM = TO.minus(Duration.ofDays(30));

    private final ReportCalculator calculator = new ReportCalculator(ReportCalculator.DEFAULT_SLA);

    /** An incident opened {@code daysAgo} days before TO, acknowledged and resolved after the given minutes. */
    private static ReportCalculator.IncidentRecord incident(String severity, int daysAgo, Integer ackMinutes,
                                                            Integer resolveMinutes, String assignee, String origin,
                                                            String firstAlert) {
        Instant opened = TO.minus(Duration.ofDays(daysAgo));
        return new ReportCalculator.IncidentRecord("INC-" + daysAgo, severity, opened,
                ackMinutes == null ? null : opened.plus(Duration.ofMinutes(ackMinutes)),
                resolveMinutes == null ? null : opened.plus(Duration.ofMinutes(resolveMinutes)),
                assignee, origin, firstAlert);
    }

    private final List<ReportCalculator.IncidentRecord> incidents = List.of(
            incident("CRITICAL", 1, 4, 30, "Rahul SRE", "orders-db", "orders-db cpu 97% > 90% for 3 min"),
            incident("CRITICAL", 5, 10, 90, "Rahul SRE", "orders-db", "orders-db cpu 95.5% > 90% for 3 min"),
            incident("WARNING", 8, 20, 200, "Ananya Admin", "payments-queue", "payments-queue latency p95 9000 ms > 5000 ms for 3 min"),
            incident("CRITICAL", 2, null, null, null, "checkout-api", "checkout-api error rate 38% > 5% for 5 min"),
            incident("CRITICAL", 45, 5, 20, "Rahul SRE", "orders-db", "orders-db cpu 97% > 90% for 3 min"));   // outside window

    @Test
    void countsIncidentsInTheWindowOnly() {
        Report report = calculator.calculate(incidents, FROM, TO);

        assertThat(report.opened()).isEqualTo(4);
        assertThat(report.resolved()).isEqualTo(3);
        assertThat(report.active()).isEqualTo(1);
        assertThat(report.bySeverity()).containsEntry("CRITICAL", 3L).containsEntry("WARNING", 1L);
    }

    @Test
    void meanTimesToAcknowledgeAndResolve() {
        Report report = calculator.calculate(incidents, FROM, TO);

        assertThat(report.mttaMinutes()).isCloseTo((4 + 10 + 20) / 3.0, within(0.01));
        assertThat(report.mttrMinutes()).isCloseTo((30 + 90 + 200) / 3.0, within(0.01));
    }

    @Test
    void slaComplianceUsesTheTargetForEachSeverity() {
        Report report = calculator.calculate(incidents, FROM, TO);

        // CRITICAL target 60 min: 30 min within, 90 min breached. WARNING target 240 min: 200 within.
        Report.Sla critical = report.sla().stream().filter(s -> s.severity().equals("CRITICAL")).findFirst().orElseThrow();
        assertThat(critical.targetMinutes()).isEqualTo(60);
        assertThat(critical.resolved()).isEqualTo(2);
        assertThat(critical.withinTarget()).isEqualTo(1);
        assertThat(report.slaCompliancePercent()).isCloseTo(200.0 / 3, within(0.01));
    }

    @Test
    void groupsRecurringFailuresIgnoringTheNumbers() {
        Report report = calculator.calculate(incidents, FROM, TO);

        assertThat(report.recurring()).singleElement().satisfies(r -> {
            assertThat(r.resource()).isEqualTo("orders-db");
            assertThat(r.signature()).isEqualTo("orders-db cpu #% > #% for # min");
            assertThat(r.occurrences()).isEqualTo(2);
            assertThat(r.lastSeen()).isEqualTo(TO.minus(Duration.ofDays(1)));
        });
    }

    @Test
    void ranksResourcesAndResponders() {
        Report report = calculator.calculate(incidents, FROM, TO);

        assertThat(report.topResources().getFirst()).isEqualTo(new Report.ResourceCount("orders-db", 2));
        assertThat(report.responders()).extracting(Report.Responder::name).containsExactly("Rahul SRE", "Ananya Admin");
        Report.Responder rahul = report.responders().getFirst();
        assertThat(rahul.resolved()).isEqualTo(2);
        assertThat(rahul.mttrMinutes()).isCloseTo(60.0, within(0.01));
    }

    @Test
    void anEmptyWindowGivesZerosNotErrors() {
        Report report = calculator.calculate(List.of(), FROM, TO);

        assertThat(report.opened()).isZero();
        assertThat(report.mttaMinutes()).isNull();
        assertThat(report.mttrMinutes()).isNull();
        assertThat(report.slaCompliancePercent()).isNull();
        assertThat(report.recurring()).isEmpty();
    }
}
