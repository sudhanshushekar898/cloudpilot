package dev.cloudpilot.incidents;

import dev.cloudpilot.common.ConflictException;

/** A lifecycle step that isn't allowed from the current status; the API returns 409. */
public class InvalidTransitionException extends ConflictException {

    public InvalidTransitionException(String message) {
        super(message);
    }
}
