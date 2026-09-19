package com.example.humancode.problem;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.humancode.config.OpenAiClientHolder;

import lombok.extern.slf4j.Slf4j;

/**
 * Production source: a fresh, model-written problem per session.
 *
 * <p>Falls back to the curated bank when generation is unavailable or the model
 * returns something unusable. An interview that cannot start is worse than a
 * familiar question, and the fallback is loud in the logs.
 */
@Component
@ConditionalOnProperty(name = "humancode.problems.source", havingValue = "generated")
@Slf4j
public class GeneratedProblemSource implements ProblemSource {

    private final ProblemGenerator generator;
    private final ProblemBank bank;
    private final OpenAiClientHolder clientHolder;

    public GeneratedProblemSource(ProblemGenerator generator, ProblemBank bank, OpenAiClientHolder clientHolder) {
        this.generator = generator;
        this.bank = bank;
        this.clientHolder = clientHolder;

        if (!clientHolder.isConfigured()) {
            log.error("""
                    humancode.problems.source=generated but OPENAI_API_KEY is not set. \
                    Every session will silently fall back to the curated bank. Set the \
                    key, or switch the property back to 'bank'.""");
        }
    }

    @Override
    public Problem next(String id) {
        // An explicit request still wins — useful for reproducing a bug report.
        if (id != null && !id.isBlank()) {
            return bank.require(id);
        }

        Optional<Problem> generated = generator.generate();
        if (generated.isPresent()) {
            return generated.get();
        }

        Problem fallback = bank.random();
        log.warn("Generation unavailable; falling back to bank problem '{}'", fallback.id());
        return fallback;
    }

    @Override
    public String describe() {
        return clientHolder.isConfigured()
                ? "model-generated (bank fallback)"
                : "model-generated [DEGRADED: no API key, always falling back to bank]";
    }
}
