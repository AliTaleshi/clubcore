package ir.clubcore.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ir.clubcore.ai.LlmClient;

/** Deterministic LLM used by integration tests; can be toggled to simulate an unavailable model. */
public class FakeLlmClient implements LlmClient {

    public record Call(String system, List<Turn> turns, Effort effort) {
    }

    public final List<Call> calls = new ArrayList<>();
    public volatile boolean available = true;
    public volatile String answer = "پاسخ آزمایشی هوش مصنوعی";

    @Override
    public boolean enabled() {
        return available;
    }

    @Override
    public String model() {
        return "fake-model";
    }

    @Override
    public synchronized Optional<String> complete(String system, List<Turn> turns, Effort effort, long maxTokens) {
        calls.add(new Call(system, List.copyOf(turns), effort));
        return available ? Optional.of(answer) : Optional.empty();
    }
}
