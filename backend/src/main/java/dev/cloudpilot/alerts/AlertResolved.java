package dev.cloudpilot.alerts;

import java.time.Instant;
import java.util.UUID;

public record AlertResolved(UUID alertId, UUID ruleId, UUID resourceId, Instant resolvedAt) {
}
