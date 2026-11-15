package dev.cloudpilot.ai;

import java.util.Locale;
import java.util.Optional;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.OutputConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /** Only exists when an API key is configured; everything else asks for it optionally. */
    @Bean
    @ConditionalOnExpression("!'${cloudpilot.ai.anthropic-api-key:}'.isBlank()")
    public ClaudeGateway claudeGateway(AiProperties properties) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(properties.anthropicApiKey())
                .timeout(properties.timeout())
                // One retry for 429/5xx; after that the rule-based or template fallback answers.
                .maxRetries(1)
                .build();
        OutputConfig.Effort effort = OutputConfig.Effort.of(properties.effort().toLowerCase(Locale.ROOT));
        log.info("Claude enabled: {} (effort {})", properties.model(), properties.effort());
        return new AnthropicClaudeGateway(client, properties.model(), effort, properties.serverFallbacks());
    }

    @Bean
    public AnalysisRunner analysisRunner(ObjectProvider<ClaudeGateway> gateway) {
        Optional<Analyzer> claude = Optional.ofNullable(gateway.getIfAvailable())
                .map(g -> new ClaudeAnalyzer(g, new PromptBuilder()));
        if (claude.isEmpty()) {
            log.info("AI analysis: rule-based only (set ANTHROPIC_API_KEY to enable Claude)");
        }
        return new AnalysisRunner(claude, new RuleBasedAnalyzer(), new AnalysisValidator());
    }
}
