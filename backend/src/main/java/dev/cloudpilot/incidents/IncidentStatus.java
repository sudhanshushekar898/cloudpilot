package dev.cloudpilot.incidents;

/**
 * The only path an incident can take. Each step has a meaning on-call teams rely on:
 * acknowledged = "someone owns this", investigating = "work is in progress",
 * resolved = "impact is over and the fix is written down".
 */
public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED;

    public boolean canMoveTo(IncidentStatus next) {
        return next.ordinal() == this.ordinal() + 1;
    }

    public boolean isActive() {
        return this != RESOLVED;
    }
}
