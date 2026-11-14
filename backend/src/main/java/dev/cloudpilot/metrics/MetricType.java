package dev.cloudpilot.metrics;

/**
 * The five signals collected for every resource. Units are fixed so every chart,
 * rule and AI prompt reads them the same way.
 */
public enum MetricType {
    CPU("%"),
    MEMORY("%"),
    LATENCY_P95("ms"),
    ERROR_RATE("%"),
    AVAILABILITY("%");

    private final String unit;

    MetricType(String unit) {
        this.unit = unit;
    }

    public String unit() {
        return unit;
    }
}
