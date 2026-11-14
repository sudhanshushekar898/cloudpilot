package dev.cloudpilot.simulator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import dev.cloudpilot.metrics.MetricSample;
import dev.cloudpilot.metrics.MetricType;
import dev.cloudpilot.resources.ResourceType;

import static dev.cloudpilot.metrics.MetricType.AVAILABILITY;
import static dev.cloudpilot.metrics.MetricType.CPU;
import static dev.cloudpilot.metrics.MetricType.ERROR_RATE;
import static dev.cloudpilot.metrics.MetricType.LATENCY_P95;
import static dev.cloudpilot.metrics.MetricType.MEMORY;

/**
 * Produces one sample per metric per resource for a point in time:
 * a typical baseline for the resource type, plus random noise, plus the active
 * scenario's effect (if any). Pure apart from the injected Random, so tests can
 * use a fixed seed.
 */
public class MetricGenerator {

    /** Typical healthy values per resource type. For a queue, "latency" is the age of the oldest message. */
    private static final Map<ResourceType, Map<MetricType, Double>> BASELINES = new EnumMap<>(Map.of(
            ResourceType.EC2, baseline(35, 55, 180, 0.4),
            ResourceType.RDS, baseline(40, 65, 15, 0.1),
            ResourceType.API, baseline(30, 50, 220, 0.5),
            ResourceType.QUEUE, baseline(10, 30, 400, 0.05),
            ResourceType.POD, baseline(45, 60, 150, 0.3)));

    /** Relative noise (standard deviation as a fraction of the value). */
    private static final double NOISE = 0.05;
    private static final double ERROR_RATE_NOISE = 0.3;
    private static final double AVAILABILITY_NOISE = 0.05;

    private final Random random;

    public MetricGenerator(Random random) {
        this.random = random;
    }

    private static Map<MetricType, Double> baseline(double cpu, double memory, double latencyMs, double errorRate) {
        return Map.of(CPU, cpu, MEMORY, memory, LATENCY_P95, latencyMs, ERROR_RATE, errorRate, AVAILABILITY, 100.0);
    }

    public List<MetricSample> generate(List<SimulatedResource> resources, Instant at, Optional<ActiveScenario> scenario) {
        List<MetricSample> samples = new ArrayList<>(resources.size() * MetricType.values().length);
        for (SimulatedResource resource : resources) {
            Map<MetricType, Double> base = BASELINES.get(resource.type());
            for (MetricType metric : MetricType.values()) {
                double target = applyScenario(base.get(metric), resource.name(), metric, at, scenario);
                samples.add(new MetricSample(resource.id(), metric, withNoise(metric, target), at));
            }
        }
        return samples;
    }

    private static double applyScenario(double base, String resourceName, MetricType metric, Instant at,
                                        Optional<ActiveScenario> scenario) {
        if (scenario.isEmpty()) {
            return base;
        }
        double progress = scenario.get().progress(at);
        for (ScenarioType.Effect effect : scenario.get().type().effects()) {
            if (effect.resourceName().equals(resourceName) && effect.metric() == metric) {
                return base + (effect.peak() - base) * effect.ramp().factor(progress);
            }
        }
        return base;
    }

    private double withNoise(MetricType metric, double value) {
        double noisy = switch (metric) {
            // Availability sits at 100 and only dips slightly, so noise is absolute, downward only.
            case AVAILABILITY -> value - Math.abs(random.nextGaussian()) * AVAILABILITY_NOISE;
            case ERROR_RATE -> value * (1 + random.nextGaussian() * ERROR_RATE_NOISE);
            default -> value * (1 + random.nextGaussian() * NOISE);
        };
        if (metric == LATENCY_P95) {
            return round(Math.max(1, noisy));
        }
        return round(Math.max(0, Math.min(100, noisy)));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
