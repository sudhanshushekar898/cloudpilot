package dev.cloudpilot.alerts;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import dev.cloudpilot.metrics.WindowStats;

/**
 * Decides whether a rule should fire or resolve for one resource. Pure logic: no clock,
 * no database, so every edge case is a fast unit test.
 *
 * <ul>
 *   <li><b>Fire</b> only if <em>every</em> sample in the rule's window breaches the threshold
 *   and the samples actually cover the window. "Error rate > 5% for 5 minutes" must mean five
 *   minutes, not "one bad sample" or "two minutes of data that happened to be bad".</li>
 *   <li><b>Resolve</b> only if every sample in the short recent window is back to normal.
 *   One good sample in the middle of an outage must not close the alert (flapping).</li>
 *   <li><b>No data</b> never resolves: a resource that stopped reporting has not recovered.</li>
 * </ul>
 */
public class RuleEvaluator {

    public enum Decision { FIRE, RESOLVE, NONE }

    /** How far the first sample may be from the window start and still count as covering it. */
    private final Duration coverageTolerance;

    public RuleEvaluator(Duration coverageTolerance) {
        this.coverageTolerance = coverageTolerance;
    }

    public Decision decide(RuleCondition rule, Optional<WindowStats> window, Optional<WindowStats> recent,
                           boolean firing, Instant windowStart) {
        if (!firing) {
            return window.filter(stats -> coversWindow(stats, windowStart))
                    .filter(stats -> allBreach(rule, stats))
                    .map(stats -> Decision.FIRE)
                    .orElse(Decision.NONE);
        }
        return recent.filter(stats -> noneBreach(rule, stats))
                .map(stats -> Decision.RESOLVE)
                .orElse(Decision.NONE);
    }

    private boolean coversWindow(WindowStats stats, Instant windowStart) {
        return stats.count() > 0 && !stats.earliest().isAfter(windowStart.plus(coverageTolerance));
    }

    /** For "> t" the smallest value must breach; for "< t" the largest must. */
    private static boolean allBreach(RuleCondition rule, WindowStats stats) {
        double worstCase = rule.operator() == Operator.GT ? stats.min() : stats.max();
        return rule.operator().breaches(worstCase, rule.threshold());
    }

    /** For "> t" even the largest value must be fine; for "< t" even the smallest. */
    private static boolean noneBreach(RuleCondition rule, WindowStats stats) {
        double bestCase = rule.operator() == Operator.GT ? stats.max() : stats.min();
        return stats.count() > 0 && !rule.operator().breaches(bestCase, rule.threshold());
    }
}
