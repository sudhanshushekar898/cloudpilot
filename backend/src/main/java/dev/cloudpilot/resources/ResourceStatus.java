package dev.cloudpilot.resources;

public enum ResourceStatus {
    HEALTHY,
    DEGRADED,
    DOWN,
    /** No recent metrics: the resource may be fine, we just can't tell. */
    UNKNOWN
}
