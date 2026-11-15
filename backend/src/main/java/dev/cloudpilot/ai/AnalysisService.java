package dev.cloudpilot.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.cloudpilot.common.NotFoundException;
import dev.cloudpilot.incidents.IncidentQueryService;
import dev.cloudpilot.incidents.IncidentViews;
import dev.cloudpilot.knowledge.KnowledgeService;
import dev.cloudpilot.metrics.EventStore;
import dev.cloudpilot.resources.ResourceRef;
import dev.cloudpilot.resources.ResourceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

/**
 * Collects an incident's evidence, runs the analysis and keeps the result.
 * Deliberately not one big transaction: the LLM call can take a while, and holding a database
 * transaction open while waiting on a network call ties up a connection.
 */
@Service
public class AnalysisService {

    public static final String DEFAULT_QUESTION = "What is the most likely root cause, and what should we check first?";

    /** Same question again within this window returns the stored answer: no duplicate cost. */
    static final Duration REUSE_WINDOW = Duration.ofSeconds(30);
    /** Any deployment this long before the first alert is shown to the analysis. */
    static final Duration DEPLOYMENT_LOOKBACK = Duration.ofHours(1);

    private final IncidentQueryService incidents;
    private final KnowledgeService knowledge;
    private final ResourceService resources;
    private final EventStore events;
    private final AnalysisRunner runner;
    private final AnalysisStore store;
    private final Clock clock;
    private final MeterRegistry registry;
    private final EvidenceBundleBuilder bundles = new EvidenceBundleBuilder(15, 4);

    public AnalysisService(IncidentQueryService incidents, KnowledgeService knowledge, ResourceService resources,
                           EventStore events, AnalysisRunner runner, AnalysisStore store, Clock clock,
                           MeterRegistry registry) {
        this.incidents = incidents;
        this.knowledge = knowledge;
        this.resources = resources;
        this.events = events;
        this.runner = runner;
        this.store = store;
        this.clock = clock;
        this.registry = registry;
    }

    public AnalysisResult analyze(UUID incidentId, String question, String requestedBy) {
        IncidentViews.Detail incident = incidents.detail(incidentId);
        String asked = question == null || question.isBlank() ? DEFAULT_QUESTION : question.trim();
        Instant now = Instant.now(clock);

        Optional<AnalysisResult> recent = store.latest(incidentId)
                .filter(a -> a.question().equals(asked) && a.createdAt().isAfter(now.minus(REUSE_WINDOW)));
        if (recent.isPresent()) {
            return recent.get();
        }

        EvidenceBundle bundle = collect(incident, asked);
        Timer.Sample sample = Timer.start(registry);
        AnalysisRunner.Outcome outcome = runner.run(bundle);
        // Latency and fallback rate per analyser: a rising "fallback" count means the AI provider is struggling.
        sample.stop(registry.timer("cloudpilot.analysis.duration",
                "analyzer", outcome.analyzer(), "outcome", outcome.fellBack() ? "fallback" : "answered"));
        AnalysisValidator.Validated v = outcome.validated();

        AnalysisResult result = new AnalysisResult(UUID.randomUUID(), incidentId, asked, v.summary(),
                v.observedEvidence(), v.likelyCause(), v.confidence(), v.confidenceReason(), v.verification(),
                v.remediation(), v.warnings(), bundle.items(), outcome.analyzer(), outcome.model(), requestedBy,
                Instant.now(clock));
        store.save(result);
        return result;
    }

    public AnalysisResult latest(UUID incidentId) {
        incidents.detail(incidentId);
        return findLatest(incidentId)
                .orElseThrow(() -> new NotFoundException("No analysis yet for incident " + incidentId));
    }

    /** For other modules (postmortems): the latest analysis, if anyone asked for one. */
    public Optional<AnalysisResult> findLatest(UUID incidentId) {
        return store.latest(incidentId);
    }

    private EvidenceBundle collect(IncidentViews.Detail incident, String question) {
        Map<UUID, ResourceRef> topology = resources.topology();

        List<IncidentViews.TimelineItem> timeline = new ArrayList<>(
                incidents.timeline(incident.id(), Set.of(IncidentViews.ItemType.LOG), null));
        // All recent deployments, not only to related services: "what changed anywhere?"
        // comes first in most incidents, and the analysis must be able to rule changes out.
        events.deployments(topology.keySet(), incident.openedAt().minus(DEPLOYMENT_LOOKBACK), Instant.now(clock))
                .forEach(d -> timeline.add(new IncidentViews.TimelineItem(IncidentViews.ItemType.DEPLOYMENT,
                        d.deployedAt(), "Deployed " + d.version() + ": " + d.description(), d.deployedBy(),
                        d.resourceId(), name(topology, d.resourceId()), null)));
        timeline.sort((a, b) -> a.occurredAt().compareTo(b.occurredAt()));

        List<String> edges = new ArrayList<>();
        for (IncidentViews.ResourceItem resource : incident.resources()) {
            ResourceRef ref = topology.get(resource.id());
            if (ref != null) {
                ref.dependsOn().forEach(callee -> edges.add(ref.name() + " -> " + name(topology, callee)));
            }
        }

        return bundles.build(incident, timeline, knowledge.suggestionsFor(incident.id(), 4), edges, question);
    }

    private static String name(Map<UUID, ResourceRef> topology, UUID id) {
        ResourceRef ref = topology.get(id);
        return ref == null ? "(unknown resource)" : ref.name();
    }
}
