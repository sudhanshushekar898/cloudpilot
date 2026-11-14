package dev.cloudpilot.alerts;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import dev.cloudpilot.metrics.WindowStats;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-06: "An alert fires only when the condition holds for the full duration."
 * Rule under test: error rate > 5 for 5 minutes.
 */
class RuleEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Instant WINDOW_START = NOW.minus(WINDOW);

    private final RuleEvaluator evaluator = new RuleEvaluator(Duration.ofSeconds(30));
    private final RuleCondition rule = new RuleCondition(Operator.GT, 5.0, WINDOW);

    /** Samples covering the whole window, with the given min/max. */
    private static Optional<WindowStats> fullWindow(double min, double max) {
        return Optional.of(new WindowStats(min, max, 30, WINDOW_START.plusSeconds(5), NOW.minusSeconds(5), max));
    }

    private static Optional<WindowStats> recent(double min, double max) {
        return Optional.of(new WindowStats(min, max, 6, NOW.minusSeconds(55), NOW.minusSeconds(5), max));
    }

    @Nested
    class WhenNotFiring {

        @Test
        void firesWhenEverySampleInTheFullWindowBreaches() {
            assertThat(evaluator.decide(rule, fullWindow(6, 30), recent(6, 30), false, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.FIRE);
        }

        @Test
        void doesNotFireWhenOneSampleInTheWindowWasBelowThreshold() {
            // A single 4.9% sample means the condition did not hold for the full 5 minutes.
            assertThat(evaluator.decide(rule, fullWindow(4.9, 30), recent(6, 30), false, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.NONE);
        }

        @Test
        void doesNotFireWhenTheThresholdIsOnlyTouched() {
            assertThat(evaluator.decide(rule, fullWindow(5.0, 5.0), recent(5, 5), false, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.NONE);
        }

        @Test
        void doesNotFireWhenDataDoesNotCoverTheWholeWindow() {
            // Breaching, but samples only go back 2 minutes: we can't claim "for 5 minutes" yet.
            Optional<WindowStats> partial = Optional.of(
                    new WindowStats(20, 30, 12, NOW.minus(Duration.ofMinutes(2)), NOW.minusSeconds(5), 30));

            assertThat(evaluator.decide(rule, partial, recent(20, 30), false, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.NONE);
        }

        @Test
        void doesNotFireWithoutData() {
            assertThat(evaluator.decide(rule, Optional.empty(), Optional.empty(), false, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.NONE);
        }

        @Test
        void lessThanRulesFireWhenEverySampleIsBelow() {
            RuleCondition availability = new RuleCondition(Operator.LT, 99.0, WINDOW);

            assertThat(evaluator.decide(availability, fullWindow(70, 98.5), recent(70, 98.5), false, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.FIRE);
            assertThat(evaluator.decide(availability, fullWindow(70, 99.5), recent(70, 99.5), false, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.NONE);
        }
    }

    @Nested
    class WhenFiring {

        @Test
        void resolvesWhenEveryRecentSampleIsBackToNormal() {
            assertThat(evaluator.decide(rule, fullWindow(0.4, 30), recent(0.3, 0.9), true, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.RESOLVE);
        }

        @Test
        void staysFiringWhileAnyRecentSampleStillBreaches() {
            // Flapping guard: one good sample isn't enough to declare recovery.
            assertThat(evaluator.decide(rule, fullWindow(0.4, 30), recent(0.4, 12), true, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.NONE);
        }

        @Test
        void staysFiringWhenDataStops() {
            // A resource that stops reporting has not recovered.
            assertThat(evaluator.decide(rule, Optional.empty(), Optional.empty(), true, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.NONE);
        }

        @Test
        void doesNotFireAgainWhileAlreadyFiring() {
            assertThat(evaluator.decide(rule, fullWindow(6, 30), recent(6, 30), true, WINDOW_START))
                    .isEqualTo(RuleEvaluator.Decision.NONE);
        }
    }
}
