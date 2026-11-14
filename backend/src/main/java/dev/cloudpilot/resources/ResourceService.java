package dev.cloudpilot.resources;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.cloudpilot.metrics.MetricStore;
import dev.cloudpilot.metrics.MetricType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResourceService {

    /** Samples older than this don't count as "current"; the simulator writes every 10 s. */
    static final Duration FRESHNESS = Duration.ofMinutes(2);

    /** Charts get at most this many points per metric, whatever the time range. */
    static final int MAX_POINTS = 120;

    private final CloudResourceRepository resources;
    private final MetricStore metrics;
    private final HealthEvaluator health;
    private final Clock clock;

    public ResourceService(CloudResourceRepository resources, MetricStore metrics, HealthEvaluator health, Clock clock) {
        this.resources = resources;
        this.metrics = metrics;
        this.health = health;
        this.clock = clock;
    }

    public List<ResourceView> list() {
        Map<UUID, Map<MetricType, Double>> latest = metrics.latestByResource(Instant.now(clock).minus(FRESHNESS));
        return resources.findAllByOrderByNameAsc().stream()
                .map(resource -> toView(resource, latest.getOrDefault(resource.getId(), Map.of())))
                .toList();
    }

    public ResourceView get(UUID id) {
        CloudResource resource = find(id);
        Map<MetricType, Double> latest = metrics.latestByResource(Instant.now(clock).minus(FRESHNESS))
                .getOrDefault(id, Map.of());
        return toView(resource, latest);
    }

    public MetricHistory history(UUID id, int minutes) {
        find(id);
        Instant to = Instant.now(clock);
        Instant from = to.minus(Duration.ofMinutes(minutes));
        long bucketSeconds = bucketSecondsFor(minutes);
        return new MetricHistory(id, minutes, bucketSeconds,
                metrics.history(id, from, to, Duration.ofSeconds(bucketSeconds)));
    }

    /** Every resource with its dependencies, keyed by id. Small and read often, so no paging. */
    public Map<UUID, ResourceRef> topology() {
        return resources.findAll().stream().collect(Collectors.toMap(
                CloudResource::getId,
                r -> new ResourceRef(r.getId(), r.getName(), r.getType(), r.getDependsOn())));
    }

    /** 15 min -> 10 s buckets, 1 h -> 30 s, 24 h -> 12 min. Never finer than the sample rate. */
    static long bucketSecondsFor(int minutes) {
        return Math.max(10, (minutes * 60L) / MAX_POINTS);
    }

    private CloudResource find(UUID id) {
        return resources.findById(id).orElseThrow(() -> new ResourceNotFoundException(id));
    }

    private ResourceView toView(CloudResource resource, Map<MetricType, Double> latest) {
        return new ResourceView(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                resource.getRegion(),
                resource.getDescription(),
                health.evaluate(latest),
                latest,
                resource.getDependsOn());
    }
}
