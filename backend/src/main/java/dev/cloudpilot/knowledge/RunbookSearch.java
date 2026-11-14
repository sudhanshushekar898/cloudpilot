package dev.cloudpilot.knowledge;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Full-text retrieval over runbook chunks using PostgreSQL's tsvector, GIN index and ts_rank_cd.
 * Heading words are weighted above body words (see the V9 migration).
 */
@Repository
public class RunbookSearch {

    private final JdbcTemplate jdbc;

    public RunbookSearch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One retrieved section. {@code snippet} marks matched words with [[ ]] instead of HTML,
     * so the UI decides how to highlight and nothing from a runbook is ever rendered as markup.
     */
    public record Hit(UUID chunkId, UUID runbookId, String runbookSlug, String runbookTitle, String heading,
                      String content, String snippet, double score) {
    }

    public List<Hit> search(String tsquery, int limit) {
        return jdbc.query("""
                        select c.id, c.runbook_id, r.slug, r.title, c.heading, c.content,
                               ts_headline('english', c.content, q,
                                           'StartSel=[[, StopSel=]], MaxFragments=2, MaxWords=25, MinWords=8') as snippet,
                               ts_rank_cd(c.search_vector, q) as score
                        from runbook_chunks c
                        join runbooks r on r.id = c.runbook_id,
                             to_tsquery('english', ?) q
                        where c.search_vector @@ q
                        order by score desc, r.title, c.ordinal
                        limit ?
                        """,
                (rs, row) -> new Hit(
                        rs.getObject("id", UUID.class), rs.getObject("runbook_id", UUID.class), rs.getString("slug"),
                        rs.getString("title"), rs.getString("heading"), rs.getString("content"),
                        rs.getString("snippet"), rs.getDouble("score")),
                tsquery, limit);
    }
}
