package dev.cloudpilot.alerts;

public enum Severity {
    /** Customer-facing impact now: page someone. */
    CRITICAL,
    /** Likely to become a problem: look during working hours. */
    WARNING,
    /** For context only. */
    INFO
}
