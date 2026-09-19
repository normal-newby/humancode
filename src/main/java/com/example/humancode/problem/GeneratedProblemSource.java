package com.example.humancode.problem;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.humancode.config.OpenAiClientHolder;

import lombok.extern.slf4j.Slf4j;

/**
 * Production source: a fresh, model-written problem per session.
 *
 * <p>Problems come off {@link ProblemPool} so the call is not made in front of
 * the candidate. Falls back to the curated bank when generation is unavailable
 * or the model returns something unusable. An interview that cannot start is
 * worse than a familiar question, and the fallback is loud in the logs.
 */
@Component
@ConditionalOnProperty(name = "humancode.problems.source", havingValue = "generated")
@Slf4j
public class GeneratedProblemSource implements ProblemSource {

    private final ProblemGenerator generator;
    private final ProblemPool pool;
    private final ProblemBank bank;
    private final OpenAiClientHolder clientHolder;

    public GeneratedProblemSource(ProblemGenerator generator, ProblemPool pool, ProblemBank bank,
            OpenAiClientHolder clientHolder) {
        this.generator = generator;
        this.pool = pool;
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
    public Problem next(String id, Difficulty difficulty, ProblemType type, ProblemRuntime runtime) {
        // An explicit request still wins — useful for reproducing a bug report.
        if (id != null && !id.isBlank()) {
            Problem problem = bank.require(id);
            if (type != null && problem.type() != type) {
                throw new IllegalArgumentException("Problem '" + id + "' is " + problem.type().label()
                        + ", not " + type.label());
            }
            if (runtime != null && !runtime.matches(problem)) {
                throw new IllegalArgumentException("Problem '" + id + "' is not a " + runtime.label()
                        + " problem");
            }
            return problem;
        }

        // Three tiers, cheapest first: something already warm, then a blocking
        // generation, then the bank. Only the first is fast enough to be
        // invisible, which is the entire point of the pool.
        Optional<Problem> warm = pool.take(difficulty, type, runtime);
        if (warm.isPresent()) {
            return warm.get();
        }

        // A generation already running means a problem is coming for the next
        // session, and starting a second one would cost another full call to
        // make this candidate wait 90 seconds. The bank is instant and correct.
        if (pool.busy(difficulty)) {
            Problem fallback = bank.random(difficulty, type, runtime);
            log.warn("Pool still filling; starting this session on bank problem '{}'", fallback.id());
            return fallback;
        }

        log.info("Nothing warm and nothing in flight; generating one inline");
        Optional<Problem> generated = generator.generate(difficulty, type, runtime);
        if (generated.isPresent()) {
            return generated.get();
        }

        Problem fallback = bank.random(difficulty, type, runtime);
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
