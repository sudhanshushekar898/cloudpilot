package dev.cloudpilot.metrics;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Log lines and deployments: the "what happened" evidence next to the metrics. */
@Repository
public class EventStore {

    private final JdbcTemplate jdbc;

    public EventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void saveLogs(Collection<LogEvent> logs) {
        if (logs.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("insert into log_events (resource_id, level, message, logged_at) values (?, ?, ?, ?)",
                logs.stream()
                        .map(l -> new Object[]{l.resourceId(), l.level().name(), l.message(), Timestamp.from(l.loggedAt())})
                        .toList());
    }

    @Transactional
    public void saveDeployment(Deployment d) {
        jdbc.update("""
                        insert into deployments (id, resource_id, version, description, deployed_by, deployed_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                d.id(), d.resourceId(), d.version(), d.description(), d.deployedBy(), Timestamp.from(d.deployedAt()));
    }

    /** Newest first, at most {@code limit} lines. */
    public List<LogEvent> logs(Collection<UUID> resourceIds, Instant from, Instant to, int limit) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                        select resource_id, level, message, logged_at from log_events
                        where resource_id = any (?) and logged_at >= ? and logged_at < ?
                        order by logged_at desc
                        limit ?
                        """,
                (rs, row) -> new LogEvent(rs.getObject("resource_id", UUID.class),
                        LogEvent.Level.valueOf(rs.getString("level")), rs.getString("message"),
                        rs.getTimestamp("logged_at").toInstant()),
                resourceIds.toArray(UUID[]::new), Timestamp.from(from), Timestamp.from(to), limit);
    }

    public List<Deployment> deployments(Collection<UUID> resourceIds, Instant from, Instant to) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                        select id, resource_id, version, description, deployed_by, deployed_at from deployments
                        where resource_id = any (?) and deployed_at >= ? and deployed_at < ?
                        order by deployed_at desc
                        """,
                (rs, row) -> new Deployment(rs.getObject("id", UUID.class), rs.getObject("resource_id", UUID.class),
                        rs.getString("version"), rs.getString("description"), rs.getString("deployed_by"),
                        rs.getTimestamp("deployed_at").toInstant()),
                resourceIds.toArray(UUID[]::new), Timestamp.from(from), Timestamp.from(to));
    }

    @Transactional
    public int deleteLogsOlderThan(Instant cutoff) {
        return jdbc.update("delete from log_events where logged_at < ?", Timestamp.from(cutoff));
    }
}
