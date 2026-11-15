package dev.cloudpilot.ai;

/**
 * LLM analysis through Claude. It only formats the evidence and returns the model's draft;
 * falling back on errors and validating the result are the caller's job, so they apply to
 * every analyser the same way.
 */
public class ClaudeAnalyzer implements Analyzer {

    private final ClaudeGateway gateway;
    private final PromptBuilder prompts;

    public ClaudeAnalyzer(ClaudeGateway gateway, PromptBuilder prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public String model() {
        return gateway.model();
    }

    @Override
    public AnalysisDraft analyze(EvidenceBundle bundle) {
        return gateway.complete(prompts.system(), prompts.user(bundle));
    }
}
