package dev.cloudpilot.resources;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(UUID id) {
        super("No resource with id " + id);
    }
}
