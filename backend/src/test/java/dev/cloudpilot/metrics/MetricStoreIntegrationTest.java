package dev.cloudpilot.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static dev.cloudpilot.metrics.MetricType.CPU;
import static dev.cloudpilot.metrics.MetricType.ERROR_RATE;
import static org.assertj.core.api.Assertions.assertThat;

class MetricStoreIntegrationTest extends IntegrationTest {

    // Seeded by Flyway V4.
    private static final UUID ORDERS_DB = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    @Autowired
    private MetricStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from metric_samples");
    }

    private static MetricSample sample(UUID resource, MetricType metric, double value, Instant at) {
        return new MetricSample(resource, metric, value, at);
    }

    @Test
    void savesSamplesInOneBatch() {
        store.saveAll(List.of(
                sample(ORDERS_DB, CPU, 40, T0),
                sample(ORDERS_DB, ERROR_RATE, 0.5, T0),
                sample(CHECKOUT_API, CPU, 20, T0)));

        assertThat(jdbc.queryForObject("select count(*) from metric_samples", Integer.class)).isEqualTo(3);
    }

    @Test
    void latestReturnsNewestValuePerResourceAndMetric() {
        store.saveAll(List.of(
                sample(ORDERS_DB, CPU, 40, T0),
                sample(ORDERS_DB, CPU, 95, T0.plusSeconds(10)),
                sample(ORDERS_DB, ERROR_RATE, 1.5, T0.plusSeconds(10)),
                sample(CHECKOUT_API, CPU, 22, T0.plusSeconds(10))));

        Map<UUID, Map<MetricType, Double>> latest = store.latestByResource(T0.minusSeconds(60));

        assertThat(latest.get(ORDERS_DB)).containsEntry(CPU, 95.0).containsEntry(ERROR_RATE, 1.5);
        assertThat(latest.get(CHECKOUT_API)).containsEntry(CPU, 22.0);
    }

    @Test
    void latestIgnoresSamplesOlderThanTheCutoff() {
        store.saveAll(List.of(sample(ORDERS_DB, CPU, 40, T0)));

        assertThat(store.latestByResource(T0.plusSeconds(1))).isEmpty();
    }

    @Test
    void historyAveragesSamplesIntoTimeBuckets() {
        store.saveAll(List.of(
                sample(ORDERS_DB, CPU, 10, T0),
                sample(ORDERS_DB, CPU, 30, T0.plusSeconds(30)),   // same minute as above -> avg 20
                sample(ORDERS_DB, CPU, 50, T0.plusSeconds(60)),   // next minute
                sample(ORDERS_DB, ERROR_RATE, 2, T0.plusSeconds(5)),
                sample(CHECKOUT_API, CPU, 99, T0)));               // other resource: excluded

        Map<MetricType, List<MetricPoint>> history =
                store.history(ORDERS_DB, T0, T0.plus(Duration.ofMinutes(5)), Duration.ofMinutes(1));

        assertThat(history.get(CPU)).containsExactly(
                new MetricPoint(T0, 20.0),
                new MetricPoint(T0.plusSeconds(60), 50.0));
        assertThat(history.get(ERROR_RATE)).containsExactly(new MetricPoint(T0, 2.0));
    }

    @Test
    void windowStatsSummarisesEachResourceInTheWindow() {
        store.saveAll(List.of(
                sample(ORDERS_DB, ERROR_RATE, 99, T0.minusSeconds(400)),     // before the window: ignored
                sample(ORDERS_DB, ERROR_RATE, 7, T0.minusSeconds(290)),
                sample(ORDERS_DB, ERROR_RATE, 12, T0.minusSeconds(100)),
                sample(ORDERS_DB, ERROR_RATE, 9, T0.minusSeconds(10)),
                sample(ORDERS_DB, CPU, 50, T0.minusSeconds(10)),             // other metric: ignored
                sample(CHECKOUT_API, ERROR_RATE, 0.5, T0.minusSeconds(20))));

        Map<UUID, WindowStats> stats = store.windowStats(ERROR_RATE, T0.minusSeconds(300), T0);

        assertThat(stats.get(ORDERS_DB)).isEqualTo(new WindowStats(
                7, 12, 3, T0.minusSeconds(290), T0.minusSeconds(10), 9));
        assertThat(stats.get(CHECKOUT_API).count()).isEqualTo(1);
    }

    @Test
    void retentionDeletesOnlyOldSamples() {
        store.saveAll(List.of(
                sample(ORDERS_DB, CPU, 1, T0.minus(Duration.ofDays(8))),
                sample(ORDERS_DB, CPU, 2, T0)));

        int deleted = store.deleteOlderThan(T0.minus(Duration.ofDays(7)));

        assertThat(deleted).isEqualTo(1);
        assertThat(store.hasSamplesSince(T0)).isTrue();
        assertThat(store.hasSamplesSince(T0.plusSeconds(1))).isFalse();
    }
}
