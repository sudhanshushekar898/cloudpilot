package dev.cloudpilot.metrics;

import java.time.Instant;
import java.util.UUID;

public record Deployment(UUID id, UUID resourceId, String version, String description, String deployedBy,
                         Instant deployedAt) {
}
