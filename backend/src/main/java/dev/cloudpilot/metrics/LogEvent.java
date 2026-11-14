package dev.cloudpilot.metrics;

import java.time.Instant;
import java.util.UUID;

public record LogEvent(UUID resourceId, Level level, String message, Instant loggedAt) {

    public enum Level { INFO, WARN, ERROR }
}
