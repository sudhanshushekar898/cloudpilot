package dev.cloudpilot.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline checks of the request we send. No API call is made: building the params runs the
 * SDK's local validation of the AnalysisDraft JSON schema, which is what would otherwise only
 * fail at runtime against the real API.
 */
class AnthropicClaudeGatewayTest {

    private final AnthropicClient client = AnthropicOkHttpClient.builder().apiKey("test-key-not-used").build();

    @Test
    void buildsAStructuredRequestForTheConfiguredModelAndEffort() {
        AnthropicClaudeGateway gateway = new AnthropicClaudeGateway(client, "claude-opus-5-5", OutputConfig.Effort.HIGH, false);

        StructuredMessageCreateParams<AnalysisDraft> params = gateway.buildParams("system prompt", "user message");

        String json = params.rawParams()._body().toString();
        assertThat(json).contains("claude-opus-5-5").contains("system prompt").contains("user message")
                .contains("json_schema").contains("likelyCause").contains("high");
        assertThat(json).doesNotContain("fallbacks");
    }

    @Test
    void canOptIntoServerSideFallbacks() {
        AnthropicClaudeGateway gateway = new AnthropicClaudeGateway(client, "claude-opus-5-5", OutputConfig.Effort.HIGH, true);

        StructuredMessageCreateParams<AnalysisDraft> params = gateway.buildParams("s", "u");

        assertThat(params.rawParams()._additionalBodyProperties()).containsKey("fallbacks");
        assertThat(params.rawParams()._headers().values("anthropic-beta")).contains(AnthropicClaudeGateway.FALLBACK_BETA);
    }
}
