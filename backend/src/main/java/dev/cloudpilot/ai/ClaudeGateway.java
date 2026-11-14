package dev.cloudpilot.ai;

/**
 * The one place that talks to the Claude API. Kept tiny so everything around it can be
 * tested with a fake, without network calls or an API key.
 */
public interface ClaudeGateway {

    /**
     * Asks the model for an analysis in the {@link AnalysisDraft} JSON shape.
     *
     * @throws ClaudeRefusalException when the model declines to answer
     * @throws RuntimeException       on API, network or parsing errors
     */
    AnalysisDraft complete(String system, String user);

    /** The model id requests go to. */
    String model();
}
