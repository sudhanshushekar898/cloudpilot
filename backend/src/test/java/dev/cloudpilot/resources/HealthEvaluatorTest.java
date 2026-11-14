package dev.cloudpilot.resources;

import java.util.EnumMap;
import java.util.Map;

import dev.cloudpilot.metrics.MetricType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static dev.cloudpilot.metrics.MetricType.AVAILABILITY;
import static dev.cloudpilot.metrics.MetricType.CPU;
import static dev.cloudpilot.metrics.MetricType.ERROR_RATE;
import static dev.cloudpilot.metrics.MetricType.LATENCY_P95;
import static dev.cloudpilot.metrics.MetricType.MEMORY;
import static org.assertj.core.api.Assertions.assertThat;

class HealthEvaluatorTest {

    private final HealthEvaluator evaluator = new HealthEvaluator();

    private static Map<MetricType, Double> healthy() {
        Map<MetricType, Double> values = new EnumMap<>(MetricType.class);
        values.put(CPU, 35.0);
        values.put(MEMORY, 50.0);
        values.put(LATENCY_P95, 120.0);
        values.put(ERROR_RATE, 0.3);
        values.put(AVAILABILITY, 100.0);
        return values;
    }

    @Test
    void normalMetricsAreHealthy() {
        assertThat(evaluator.evaluate(healthy())).isEqualTo(ResourceStatus.HEALTHY);
    }

    @Test
    void noDataIsUnknown() {
        assertThat(evaluator.evaluate(Map.of())).isEqualTo(ResourceStatus.UNKNOWN);
    }

    @ParameterizedTest(name = "{0} = {1} -> {2}")
    @CsvSource({
            "CPU,          89.9, HEALTHY",
            "CPU,          90,   DEGRADED",
            "MEMORY,       92,   DEGRADED",
            "LATENCY_P95,  999,  HEALTHY",
            "LATENCY_P95,  1000, DEGRADED",
            "ERROR_RATE,   4.9,  HEALTHY",
            "ERROR_RATE,   5,    DEGRADED",
            "AVAILABILITY, 99,   HEALTHY",
            "AVAILABILITY, 98.9, DEGRADED",
            "AVAILABILITY, 89.9, DOWN",
    })
    void thresholdsAreApplied(MetricType metric, double value, ResourceStatus expected) {
        Map<MetricType, Double> values = healthy();
        values.put(metric, value);

        assertThat(evaluator.evaluate(values)).isEqualTo(expected);
    }

    @Test
    void downWinsOverDegraded() {
        Map<MetricType, Double> values = healthy();
        values.put(ERROR_RATE, 40.0);
        values.put(AVAILABILITY, 50.0);

        assertThat(evaluator.evaluate(values)).isEqualTo(ResourceStatus.DOWN);
    }

    @Test
    void partialDataIsJudgedOnWhatIsPresent() {
        assertThat(evaluator.evaluate(Map.of(ERROR_RATE, 12.0))).isEqualTo(ResourceStatus.DEGRADED);
    }
}
