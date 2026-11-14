package dev.cloudpilot.metrics;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes metric samples with plain JDBC instead of JPA.
 *
 * Why: samples are write-heavy and never updated. JPA can't batch inserts into a table
 * with an IDENTITY/BIGSERIAL key (it must read back each id), and the reads are
 * aggregations (latest value, time buckets) that SQL does far better than entities.
 */
@Repository
public class MetricStore {

    private final JdbcTemplate jdbc;

    public MetricStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void saveAll(Collection<MetricSample> samples) {
        if (samples.isEmpty()) {
            return;
        }
        List<Object[]> rows = samples.stream()
                .map(s -> new Object[]{s.resourceId(), s.metric().name(), s.value(), Timestamp.from(s.recordedAt())})
                .toList();
        jdbc.batchUpdate(
                "insert into metric_samples (resource_id, metric, value, recorded_at) values (?, ?, ?, ?)",
                rows);
    }

    /** Newest value of each metric for each resource, ignoring samples older than {@code since}. */
    public Map<UUID, Map<MetricType, Double>> latestByResource(Instant since) {
        Map<UUID, Map<MetricType, Double>> result = new HashMap<>();
        // DISTINCT ON keeps the first row per (resource, metric) in ORDER BY order: the newest one.
        jdbc.query("""
                        select distinct on (resource_id, metric) resource_id, metric, value
                        from metric_samples
                        where recorded_at >= ?
                        order by resource_id, metric, recorded_at desc
                        """,
                rs -> {
                    UUID resourceId = rs.getObject("resource_id", UUID.class);
                    result.computeIfAbsent(resourceId, id -> new EnumMap<>(MetricType.class))
                            .put(MetricType.valueOf(rs.getString("metric")), rs.getDouble("value"));
                },
                Timestamp.from(since));
        return result;
    }

    /**
     * Average value per time bucket, for charts. A one-hour chart at 10-second sampling has
     * 360 raw points per metric; bucketing by minute sends 60 and keeps the chart readable.
     */
    public Map<MetricType, List<MetricPoint>> history(UUID resourceId, Instant from, Instant to, Duration bucket) {
        Map<MetricType, List<MetricPoint>> result = new EnumMap<>(MetricType.class);
        jdbc.query("""
                        select metric,
                               date_bin(make_interval(secs => ?), recorded_at, timestamptz '2000-01-01') as bucket,
                               avg(value) as value
                        from metric_samples
                        where resource_id = ? and recorded_at >= ? and recorded_at < ?
                        group by metric, bucket
                        order by metric, bucket
                        """,
                rs -> {
                    MetricType metric = MetricType.valueOf(rs.getString("metric"));
                    result.computeIfAbsent(metric, m -> new ArrayList<>())
                            .add(new MetricPoint(rs.getTimestamp("bucket").toInstant(), rs.getDouble("value")));
                },
                (double) bucket.toSeconds(), resourceId, Timestamp.from(from), Timestamp.from(to));
        return result;
    }

    /**
     * Min, max, count, first/last time and latest value of one metric per resource in
     * [from, to). One query for all resources, so evaluating a rule is one round trip.
     */
    public Map<UUID, WindowStats> windowStats(MetricType metric, Instant from, Instant to) {
        Map<UUID, WindowStats> result = new HashMap<>();
        jdbc.query("""
                        select resource_id,
                               min(value) as min_value,
                               max(value) as max_value,
                               count(*) as sample_count,
                               min(recorded_at) as earliest,
                               max(recorded_at) as latest,
                               (array_agg(value order by recorded_at desc))[1] as latest_value
                        from metric_samples
                        where metric = ? and recorded_at >= ? and recorded_at < ?
                        group by resource_id
                        """,
                rs -> {
                    result.put(rs.getObject("resource_id", UUID.class), new WindowStats(
                            rs.getDouble("min_value"),
                            rs.getDouble("max_value"),
                            rs.getLong("sample_count"),
                            rs.getTimestamp("earliest").toInstant(),
                            rs.getTimestamp("latest").toInstant(),
                            rs.getDouble("latest_value")));
                },
                metric.name(), Timestamp.from(from), Timestamp.from(to));
        return result;
    }

    public boolean hasSamplesSince(Instant since) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from metric_samples where recorded_at >= ?)",
                Boolean.class, Timestamp.from(since)));
    }

    @Transactional
    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("delete from metric_samples where recorded_at < ?", Timestamp.from(cutoff));
    }
}
