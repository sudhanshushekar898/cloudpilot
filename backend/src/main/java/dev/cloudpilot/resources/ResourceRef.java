package dev.cloudpilot.resources;

import java.util.Set;
import java.util.UUID;

/** Lightweight, read-only view of a resource for other modules (no JPA entity leaks out). */
public record ResourceRef(UUID id, String name, ResourceType type, Set<UUID> dependsOn) {
}
