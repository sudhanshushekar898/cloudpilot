package dev.cloudpilot.common;

/** Base class for "the thing you asked for doesn't exist" errors; mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
