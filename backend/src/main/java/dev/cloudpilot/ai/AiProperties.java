package dev.cloudpilot.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI settings (cloudpilot.ai.*).
 *
 * @param anthropicApiKey empty = use the rule-based analyser only
 * @param model           Claude model id
 * @param effort          low | medium | high | xhigh | max (Claude Opus 5.5 defaults to medium; RCA uses high)
 * @param serverFallbacks retry refused requests on another model server-side (beta)
 * @param timeout         per-request timeout; on timeout the rule-based analysis is shown instead
 */
@ConfigurationProperties(prefix = "cloudpilot.ai")
public record AiProperties(String anthropicApiKey, String model, String effort, boolean serverFallbacks,
                           Duration timeout) {

    public AiProperties {
        if (model == null || model.isBlank()) {
            model = "claude-opus-5-5";
        }
        if (effort == null || effort.isBlank()) {
            effort = "high";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(120);
        }
    }

    public boolean claudeEnabled() {
        return anthropicApiKey != null && !anthropicApiKey.isBlank();
    }
}
