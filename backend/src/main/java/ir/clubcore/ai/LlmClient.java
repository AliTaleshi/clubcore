package ir.clubcore.ai;

import java.util.List;
import java.util.Optional;

/** Text-generation backend. Returns empty when the model is not configured, unreachable, or refuses. */
public interface LlmClient {

    enum Speaker {
        USER, ASSISTANT
    }

    enum Effort {
        LOW, MEDIUM, HIGH
    }

    record Turn(Speaker speaker, String text) {
    }

    boolean enabled();

    String model();

    Optional<String> complete(String system, List<Turn> turns, Effort effort, long maxTokens);
}
