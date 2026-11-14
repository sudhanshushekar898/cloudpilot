package dev.cloudpilot.resources;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.cloudpilot.metrics.MetricType;

/** A resource as the dashboard sees it: identity, current status and latest values. */
public record ResourceView(
        UUID id,
        String name,
        ResourceType type,
        String region,
        String description,
        ResourceStatus status,
        Map<MetricType, Double> latest,
        Set<UUID> dependsOn) {
}
