package dev.cloudpilot.ai;

/** The model returned stop_reason "refusal" instead of an answer. */
public class ClaudeRefusalException extends RuntimeException {

    public ClaudeRefusalException(String category) {
        super("Claude declined to answer" + (category == null ? "" : " (" + category + ")"));
    }
}
