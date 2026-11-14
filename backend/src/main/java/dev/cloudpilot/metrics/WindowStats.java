package dev.cloudpilot.metrics;

import java.time.Instant;

/**
 * Summary of one resource's samples for one metric inside a time window.
 * Enough to answer "did the condition hold for the whole window?" without
 * loading every sample.
 */
public record WindowStats(double min, double max, long count, Instant earliest, Instant latest, double latestValue) {
}
