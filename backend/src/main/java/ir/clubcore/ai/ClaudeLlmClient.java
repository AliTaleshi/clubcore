package ir.clubcore.ai;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlock;

import ir.clubcore.config.AppProperties;

/** Claude via the official Anthropic Java SDK. */
@Component
public class ClaudeLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmClient.class);

    private final AnthropicClient client;
    private final String model;

    public ClaudeLlmClient(AppProperties props) {
        AppProperties.Ai ai = props.ai();
        this.model = ai.model();
        this.client = ai.enabled()
                ? AnthropicOkHttpClient.builder().apiKey(ai.apiKey()).timeout(Duration.ofSeconds(120)).maxRetries(1)
                        .build()
                : null;
    }

    @Override
    public boolean enabled() {
        return client != null;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public Optional<String> complete(String system, List<Turn> turns, Effort effort, long maxTokens) {
        if (client == null || turns.isEmpty()) {
            return Optional.empty();
        }
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .outputConfig(OutputConfig.builder().effort(switch (effort) {
                    case LOW -> OutputConfig.Effort.LOW;
                    case MEDIUM -> OutputConfig.Effort.MEDIUM;
                    case HIGH -> OutputConfig.Effort.HIGH;
                }).build())
                // Server-side refusal fallback: if the primary model declines, the API retries on a fallback model.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        for (Turn t : turns) {
            if (t.speaker() == Speaker.USER) {
                b.addUserMessage(t.text());
            } else {
                b.addAssistantMessage(t.text());
            }
        }
        try {
            Message msg = client.messages().create(b.build());
            String stop = msg.stopReason().map(Object::toString).orElse("");
            if ("refusal".equalsIgnoreCase(stop)) {
                log.info("Claude declined the request");
                return Optional.empty();
            }
            String text = msg.content().stream().flatMap(c -> c.text().stream()).map(TextBlock::text)
                    .collect(Collectors.joining()).trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.warn("Claude call failed, using local fallback: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
