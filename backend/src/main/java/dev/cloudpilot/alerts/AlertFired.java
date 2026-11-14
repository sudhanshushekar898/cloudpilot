package dev.cloudpilot.alerts;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an alert starts firing. The incidents module (M5) listens for this
 * to group alerts into incidents, so alerts never has to know incidents exist.
 */
public record AlertFired(UUID alertId, UUID ruleId, UUID resourceId, Severity severity, String message, Instant firedAt) {
}
