package dev.cloudpilot.alerts;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.cloudpilot.metrics.MetricSample;
import dev.cloudpilot.metrics.MetricStore;
import dev.cloudpilot.metrics.MetricType;
import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;

/** US-06 end to end: samples in PostgreSQL -> engine -> alerts and events. */
@RecordApplicationEvents
class AlertEngineIntegrationTest extends IntegrationTest {

    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CATALOG_API = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID API_ERROR_RULE = UUID.fromString("10000000-0000-0000-0000-000000000001");

    // Truncated so the sample timestamps line up exactly.
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private AlertEngine engine;

    @Autowired
    private AlertRepository alerts;

    @Autowired
    private MetricStore metrics;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from alerts");
        jdbc.update("delete from metric_samples");
    }

    /** One sample every 10 s for {@code resource} from {@code from} up to (not including) {@code to}. */
    private void errorRate(UUID resource, double value, Instant from, Instant to) {
        List<MetricSample> samples = new ArrayList<>();
        for (Instant at = from; at.isBefore(to); at = at.plusSeconds(10)) {
            samples.add(new MetricSample(resource, MetricType.ERROR_RATE, value, at));
        }
        metrics.saveAll(samples);
    }

    private List<Alert> firing() {
        return alerts.findAll().stream().filter(a -> a.getStatus() == Alert.Status.FIRING).toList();
    }

    @Test
    void firesOnceWhenErrorRateStaysHighForFiveMinutes() {
        errorRate(CHECKOUT_API, 18.0, now.minus(Duration.ofMinutes(6)), now.plusSeconds(1));
        errorRate(CATALOG_API, 0.4, now.minus(Duration.ofMinutes(6)), now.plusSeconds(1));

        engine.evaluate(now);

        assertThat(firing()).singleElement().satisfies(alert -> {
            assertThat(alert.getRuleId()).isEqualTo(API_ERROR_RULE);
            assertThat(alert.getResourceId()).isEqualTo(CHECKOUT_API);
            assertThat(alert.getSeverity()).isEqualTo(Severity.CRITICAL);
            assertThat(alert.getMessage()).isEqualTo("checkout-api error rate 18% > 5% for 5 min");
            assertThat(alert.getObservedValue()).isEqualTo(18.0);
        });
        assertThat(events.stream(AlertFired.class)).hasSize(1);
    }

    @Test
    void doesNotDuplicateAnAlertThatIsAlreadyFiring() {
        errorRate(CHECKOUT_API, 18.0, now.minus(Duration.ofMinutes(6)), now.plusSeconds(31));

        engine.evaluate(now);
        engine.evaluate(now.plusSeconds(30));

        assertThat(alerts.findAll()).hasSize(1);
        assertThat(events.stream(AlertFired.class)).hasSize(1);
    }

    @Test
    void doesNotFireWhenTheSpikeIsShorterThanTheRuleDuration() {
        errorRate(CHECKOUT_API, 0.4, now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(2)));
        errorRate(CHECKOUT_API, 25.0, now.minus(Duration.ofMinutes(2)), now.plusSeconds(1));

        engine.evaluate(now);

        assertThat(alerts.findAll()).isEmpty();
    }

    @Test
    void resolvesAfterAFullMinuteOfNormalValues() {
        errorRate(CHECKOUT_API, 18.0, now.minus(Duration.ofMinutes(6)), now.plusSeconds(1));
        engine.evaluate(now);

        errorRate(CHECKOUT_API, 0.3, now.plusSeconds(10), now.plusSeconds(81));
        engine.evaluate(now.plusSeconds(80));

        Alert alert = alerts.findAll().getFirst();
        assertThat(alert.getStatus()).isEqualTo(Alert.Status.RESOLVED);
        assertThat(alert.getResolvedAt()).isEqualTo(now.plusSeconds(80));
        assertThat(events.stream(AlertResolved.class)).hasSize(1);
    }

    @Test
    void disabledRulesAreSkipped() {
        jdbc.update("update alert_rules set enabled = false where id = ?", API_ERROR_RULE);
        try {
            errorRate(CHECKOUT_API, 18.0, now.minus(Duration.ofMinutes(6)), now.plusSeconds(1));

            engine.evaluate(now);

            assertThat(alerts.findAll()).isEmpty();
        } finally {
            jdbc.update("update alert_rules set enabled = true where id = ?", API_ERROR_RULE);
        }
    }
}
