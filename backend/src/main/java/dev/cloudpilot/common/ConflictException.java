package dev.cloudpilot.common;

/** The request is valid but clashes with current state (duplicate name, wrong status); mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
