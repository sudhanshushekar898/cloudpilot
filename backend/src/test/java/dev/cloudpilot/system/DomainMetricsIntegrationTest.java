package dev.cloudpilot.system;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.cloudpilot.ai.AnalysisService;
import dev.cloudpilot.alerts.AlertEngine;
import dev.cloudpilot.auth.Role;
import dev.cloudpilot.incidents.IncidentRepository;
import dev.cloudpilot.incidents.IncidentService;
import dev.cloudpilot.metrics.MetricSample;
import dev.cloudpilot.metrics.MetricStore;
import dev.cloudpilot.metrics.MetricType;
import dev.cloudpilot.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** The product's own metrics: what an SRE would put on a dashboard about CloudPilot itself. */
class DomainMetricsIntegrationTest extends IntegrationTest {

    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private AlertEngine engine;

    @Autowired
    private MetricStore metrics;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private AnalysisService analyses;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from incidents");
        jdbc.update("delete from alerts");
        jdbc.update("delete from metric_samples");
    }

    private double count(String name, String... tags) {
        var counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void countsAlertsIncidentsResolutionsAndAnalyses() {
        double alertsBefore = count("cloudpilot.alerts.fired", "severity", "CRITICAL");
        double incidentsBefore = count("cloudpilot.incidents.opened", "severity", "CRITICAL");

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<MetricSample> samples = new ArrayList<>();
        for (Instant at = now.minus(Duration.ofMinutes(6)); !at.isAfter(now); at = at.plusSeconds(10)) {
            samples.add(new MetricSample(CHECKOUT_API, MetricType.ERROR_RATE, 30, at));
        }
        metrics.saveAll(samples);
        engine.evaluate(now);

        assertThat(count("cloudpilot.alerts.fired", "severity", "CRITICAL")).isEqualTo(alertsBefore + 1);
        assertThat(count("cloudpilot.incidents.opened", "severity", "CRITICAL")).isEqualTo(incidentsBefore + 1);
        assertThat(registry.find("cloudpilot.incidents.active").gauge().value()).isEqualTo(1.0);

        UUID incidentId = incidents.findAll().getFirst().getId();
        analyses.analyze(incidentId, "Why?", "it@test.dev");
        assertThat(registry.find("cloudpilot.analysis.duration").tags("analyzer", "rule-based", "outcome", "answered")
                .timer().count()).isPositive();

        var actor = new IncidentService.Actor(userIdFor(Role.SRE), "it-sre@test.dev");
        incidentService.acknowledge(incidentId, actor);
        incidentService.startInvestigating(incidentId, actor);
        incidentService.resolve(incidentId, actor, "Rolled back");

        assertThat(registry.find("cloudpilot.incidents.time.to.resolve").tags("severity", "CRITICAL").timer().count())
                .isPositive();
        assertThat(registry.find("cloudpilot.incidents.active").gauge().value()).isZero();
    }

    private UUID userIdFor(Role role) {
        authHeaders(role);   // creates the user
        return jdbc.queryForObject("select id from users where email = ?", UUID.class,
                "it-" + role.name().toLowerCase() + "@test.dev");
    }
}
