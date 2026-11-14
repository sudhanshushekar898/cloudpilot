package dev.cloudpilot.resources;

import java.util.Map;

import dev.cloudpilot.metrics.MetricType;
import org.springframework.stereotype.Component;

/**
 * Turns the latest metric values into a coarse status for the dashboard cards.
 *
 * This is a snapshot ("how does it look right now?"). Alert rules in M4 are different:
 * they require a condition to hold for a duration before firing, so one noisy sample
 * changes a card's colour but does not page anyone.
 */
@Component
public class HealthEvaluator {

    static final double DOWN_AVAILABILITY_BELOW = 90.0;
    static final double DEGRADED_AVAILABILITY_BELOW = 99.0;
    static final double DEGRADED_ERROR_RATE_AT = 5.0;
    static final double DEGRADED_LATENCY_MS_AT = 1000.0;
    static final double DEGRADED_UTILISATION_AT = 90.0;

    public ResourceStatus evaluate(Map<MetricType, Double> latest) {
        if (latest.isEmpty()) {
            return ResourceStatus.UNKNOWN;
        }

        Double availability = latest.get(MetricType.AVAILABILITY);
        if (availability != null && availability < DOWN_AVAILABILITY_BELOW) {
            return ResourceStatus.DOWN;
        }

        if (below(availability, DEGRADED_AVAILABILITY_BELOW)
                || atLeast(latest.get(MetricType.ERROR_RATE), DEGRADED_ERROR_RATE_AT)
                || atLeast(latest.get(MetricType.LATENCY_P95), DEGRADED_LATENCY_MS_AT)
                || atLeast(latest.get(MetricType.CPU), DEGRADED_UTILISATION_AT)
                || atLeast(latest.get(MetricType.MEMORY), DEGRADED_UTILISATION_AT)) {
            return ResourceStatus.DEGRADED;
        }
        return ResourceStatus.HEALTHY;
    }

    private static boolean atLeast(Double value, double threshold) {
        return value != null && value >= threshold;
    }

    private static boolean below(Double value, double threshold) {
        return value != null && value < threshold;
    }
}
