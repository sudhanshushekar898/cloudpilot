package dev.cloudpilot.metrics;

import java.time.Instant;

/** One point on a chart: the average value of a metric in the bucket starting at {@code timestamp}. */
public record MetricPoint(Instant timestamp, double value) {
}
