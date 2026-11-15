package dev.cloudpilot.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StructuredTextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claude API calls through the official Anthropic Java SDK.
 *
 * <ul>
 *   <li>Structured outputs: the answer must match the {@link AnalysisDraft} JSON schema, which
 *       the SDK derives from the record and validates locally before returning it.</li>
 *   <li>Thinking: omitted, so Claude Opus 5.5 runs adaptive thinking (it can't be turned off);
 *       depth is set with effort.</li>
 *   <li>Refusals are checked before reading the content. Server-side fallbacks (beta) can be
 *       switched on so a refused request is retried on another model by the API itself.</li>
 * </ul>
 */
public class AnthropicClaudeGateway implements ClaudeGateway {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClaudeGateway.class);

    /** Plenty for a structured analysis; well under the SDK's non-streaming timeout limits. */
    static final long MAX_TOKENS = 16_000L;
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final String model;
    private final OutputConfig.Effort effort;
    private final boolean serverFallbacks;

    public AnthropicClaudeGateway(AnthropicClient client, String model, OutputConfig.Effort effort, boolean serverFallbacks) {
        this.client = client;
        this.model = model;
        this.effort = effort;
        this.serverFallbacks = serverFallbacks;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public AnalysisDraft complete(String system, String user) {
        long started = System.nanoTime();
        StructuredMessage<AnalysisDraft> response = client.messages().create(buildParams(system, user));
        log.info("Claude analysis took {} ms, usage {}", (System.nanoTime() - started) / 1_000_000, response.usage());

        if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            String category = response.stopDetails()
                    .flatMap(details -> details.category().map(Object::toString))
                    .orElse(null);
            throw new ClaudeRefusalException(category);
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Claude returned no text content"));
    }

    /** Builds the request; separate so tests can check it (including the local schema check) offline. */
    StructuredMessageCreateParams<AnalysisDraft> buildParams(String system, String user) {
        StructuredMessageCreateParams.Builder<AnalysisDraft> builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(system)
                .addUserMessage(user)
                .outputConfig(StructuredOutputConfig.<AnalysisDraft>builder()
                        .effort(effort)
                        .format(AnalysisDraft.class, JsonSchemaLocalValidation.YES)
                        .build());
        if (serverFallbacks) {
            // "default" lets the API pick the fallback model per refusal category.
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return builder.build();
    }
}
