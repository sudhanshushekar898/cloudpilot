package dev.cloudpilot.metrics;

import java.time.Instant;
import java.util.UUID;

public record MetricSample(UUID resourceId, MetricType metric, double value, Instant recordedAt) {
}
