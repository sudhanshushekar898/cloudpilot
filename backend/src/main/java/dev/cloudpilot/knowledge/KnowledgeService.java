package dev.cloudpilot.knowledge;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.cloudpilot.common.ConflictException;
import dev.cloudpilot.common.NotFoundException;
import dev.cloudpilot.incidents.IncidentQueryService;
import dev.cloudpilot.incidents.IncidentViews;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class KnowledgeService {

    static final int MAX_CHUNK_CHARS = 1200;
    /** Two sections per runbook at most, so one long runbook can't crowd out the others. */
    static final int MAX_HITS_PER_RUNBOOK = 2;

    private final RunbookRepository runbooks;
    private final RunbookChunkRepository chunks;
    private final RunbookSearch search;
    private final IncidentQueryService incidents;
    private final Clock clock;
    private final MarkdownChunker chunker = new MarkdownChunker(MAX_CHUNK_CHARS);
    private final SearchQueryBuilder queryBuilder = new SearchQueryBuilder(30);

    public KnowledgeService(RunbookRepository runbooks, RunbookChunkRepository chunks, RunbookSearch search,
                            IncidentQueryService incidents, Clock clock) {
        this.runbooks = runbooks;
        this.chunks = chunks;
        this.search = search;
        this.incidents = incidents;
        this.clock = clock;
    }

    public record RunbookSummary(UUID id, String slug, String title, long chunkCount, String createdBy,
                                 Instant updatedAt) {
    }

    public record RunbookDetail(UUID id, String slug, String title, String content, String createdBy,
                                Instant updatedAt) {
    }

    public List<RunbookSummary> list() {
        Map<UUID, Long> counts = new HashMap<>();
        runbooks.countChunksByRunbook().forEach(row ->
                counts.put((UUID) row.get("runbookId"), ((Number) row.get("chunks")).longValue()));
        return runbooks.findAllByOrderByTitleAsc().stream()
                .map(r -> new RunbookSummary(r.getId(), r.getSlug(), r.getTitle(), counts.getOrDefault(r.getId(), 0L),
                        r.getCreatedBy(), r.getUpdatedAt()))
                .toList();
    }

    public RunbookDetail get(String slug) {
        Runbook r = find(slug);
        return new RunbookDetail(r.getId(), r.getSlug(), r.getTitle(), r.getContent(), r.getCreatedBy(),
                r.getUpdatedAt());
    }

    @Transactional
    public RunbookSummary create(String title, String content, String actor) {
        String slug = slugify(title);
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Title must contain letters or digits");
        }
        if (runbooks.existsBySlug(slug)) {
            throw new ConflictException("A runbook called '" + title.trim() + "' already exists");
        }
        Runbook runbook = runbooks.save(new Runbook(slug, title.trim(), content, actor, Instant.now(clock)));
        List<RunbookChunk> sections = chunker.chunk(runbook.getTitle(), content).stream()
                .map(c -> new RunbookChunk(runbook.getId(), c.ordinal(), c.heading(), c.content()))
                .toList();
        chunks.saveAll(sections);
        return new RunbookSummary(runbook.getId(), slug, runbook.getTitle(), sections.size(), actor,
                runbook.getUpdatedAt());
    }

    @Transactional
    public void delete(String slug) {
        runbooks.delete(find(slug));
    }

    public List<RunbookSearch.Hit> search(String text, int limit) {
        return queryBuilder.build(text)
                .map(query -> diversify(search.search(query, limit * 3), limit))
                .orElse(List.of());
    }

    /**
     * Runbook sections relevant to an incident, found from its own evidence: alert messages,
     * affected resource names and recent error logs. This is the retrieval step that M7's AI
     * analysis will be grounded on.
     */
    public List<RunbookSearch.Hit> suggestionsFor(UUID incidentId, int limit) {
        IncidentViews.Detail incident = incidents.detail(incidentId);
        List<String> evidence = new ArrayList<>();
        evidence.add(incident.title());
        incident.alerts().forEach(a -> evidence.add(a.message()));
        incident.resources().forEach(r -> evidence.add(r.name()));
        List<IncidentViews.TimelineItem> logs = incidents.timeline(incidentId, Set.of(IncidentViews.ItemType.LOG), null);
        logs.subList(Math.max(0, logs.size() - 20), logs.size()).forEach(l -> evidence.add(l.message()));
        return search(String.join(" ", evidence), limit);
    }

    private static List<RunbookSearch.Hit> diversify(List<RunbookSearch.Hit> hits, int limit) {
        Map<UUID, Integer> perRunbook = new HashMap<>();
        return hits.stream()
                .filter(h -> perRunbook.merge(h.runbookId(), 1, Integer::sum) <= MAX_HITS_PER_RUNBOOK)
                .limit(limit)
                .collect(Collectors.toList());
    }

    static String slugify(String title) {
        String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.length() > 120 ? slug.substring(0, 120) : slug;
    }

    private Runbook find(String slug) {
        return runbooks.findBySlug(slug).orElseThrow(() -> new NotFoundException("No runbook " + slug));
    }
}
