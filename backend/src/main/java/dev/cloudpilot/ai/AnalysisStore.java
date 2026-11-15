package dev.cloudpilot.ai;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Stores analyses as JSONB. The result is a read-only document that is never queried by its
 * fields, so a document column is simpler than a dozen tables for claims, steps and citations.
 */
@Repository
public class AnalysisStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AnalysisStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void save(AnalysisResult result) {
        jdbc.update("""
                        insert into incident_analyses (id, incident_id, analyzer, model, requested_by, created_at, result)
                        values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
                        """,
                result.id(), result.incidentId(), result.analyzer(), result.model(), result.requestedBy(),
                Timestamp.from(result.createdAt()), toJson(result));
    }

    public Optional<AnalysisResult> latest(UUID incidentId) {
        List<AnalysisResult> rows = jdbc.query(
                "select result::text from incident_analyses where incident_id = ? order by created_at desc limit 1",
                (rs, row) -> fromJson(rs.getString(1)), incidentId);
        return rows.stream().findFirst();
    }

    private String toJson(AnalysisResult result) {
        try {
            return json.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise analysis", e);
        }
    }

    private AnalysisResult fromJson(String text) {
        try {
            return json.readValue(text, AnalysisResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read stored analysis", e);
        }
    }
}
