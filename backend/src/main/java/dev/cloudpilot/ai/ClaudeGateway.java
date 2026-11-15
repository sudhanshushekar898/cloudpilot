package dev.cloudpilot.ai;

/**
 * The one place that talks to the Claude API. Kept tiny so everything around it can be
 * tested with a fake, without network calls or an API key.
 */
public interface ClaudeGateway {

    /**
     * Asks the model for an answer in the JSON shape of {@code type} (a record; its schema is
     * derived from the record and enforced by structured outputs).
     *
     * @throws ClaudeRefusalException when the model declines to answer
     * @throws RuntimeException       on API, network or parsing errors
     */
    <T> T complete(String system, String user, Class<T> type);

    /** The model id requests go to. */
    String model();
}
