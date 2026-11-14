package dev.cloudpilot.resources;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudpilot.metrics.MetricPoint;
import dev.cloudpilot.metrics.MetricType;

public record MetricHistory(UUID resourceId, int minutes, long bucketSeconds, Map<MetricType, List<MetricPoint>> series) {
}
