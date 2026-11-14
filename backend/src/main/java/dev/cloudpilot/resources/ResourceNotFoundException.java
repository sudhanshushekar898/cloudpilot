package dev.cloudpilot.resources;

import java.util.UUID;

import dev.cloudpilot.common.NotFoundException;

public class ResourceNotFoundException extends NotFoundException {

    public ResourceNotFoundException(UUID id) {
        super("No resource with id " + id);
    }
}
