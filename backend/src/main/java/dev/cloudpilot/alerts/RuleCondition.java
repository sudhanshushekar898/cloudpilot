package dev.cloudpilot.alerts;

import java.time.Duration;

/** The part of a rule that decides firing: "value OPERATOR threshold for DURATION". */
public record RuleCondition(Operator operator, double threshold, Duration duration) {
}
