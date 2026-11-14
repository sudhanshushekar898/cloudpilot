package dev.cloudpilot.simulator;

import java.util.UUID;

import dev.cloudpilot.resources.ResourceType;

/** The little the simulator needs to know about a resource. */
public record SimulatedResource(UUID id, String name, ResourceType type) {
}
