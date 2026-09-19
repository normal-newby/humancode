package com.example.humancode.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything tunable about a session lives here rather than in code, so tone,
 * pacing and model choice can be changed without a recompile.
 */
@ConfigurationProperties(prefix = "humancode")
public record HumancodeProperties(Ai ai, Interview interview, Problems problems) {

    public record Ai(
            String apiKey,
            /** Deliberate path: problem statements, hints, curveballs, report card. */
            String model,
            /** Quip path: reactive one-liners. Fires often, must feel instant. */
            String quipModel,
            Duration requestTimeout) {
    }

    public record Problems(
            /** {@code bank} in dev, {@code generated} in prod. */
            @DefaultValue("bank") String source,
            /**
             * How many generated problems to keep warm.
             *
             * <p>Only meaningful with {@code source=generated}. Each slot is one
             * model call, paid once and reused by the next session to start, so
             * this is the whole cost of not making the candidate watch a
             * spinner. Zero disables pre-generation and restores the blocking
             * call in front of the begin button.
             */
            @DefaultValue("2") int poolSize,
            /**
             * Deadline for one generation call.
             *
             * <p>Generous on purpose. Writing a problem with correct tests takes
             * a reasoning model well over a minute, and the client-wide 30s
             * default does not fail the call, it silently <em>retries</em> it —
             * so a short timeout here does not save time, it multiplies the
             * bill by the retry count and hides it.
             */
            @DefaultValue("180s") Duration generationTimeout,
            /**
             * Where the warm pool is kept between restarts.
             *
             * <p>Generated problems are otherwise thrown away on shutdown, which
             * means the first session after every restart gets a bank problem
             * while the pool spends a minute regenerating what it already had.
             * Blank disables the cache.
             */
            @DefaultValue("./data/problem-pool.json") String cacheFile) {
    }

    public record Interview(
            /** Silence longer than this counts as idle and arms the idle trigger. */
            Duration idleThreshold,
            /** Hard floor between two interviewer utterances. Protects the joke. */
            Duration quipCooldown,
            Duration telemetryBatchWindow,
            /**
             * How far into a session the curveball may first fire. Gives the
             * candidate room to actually get somewhere before the scope changes
             * on them — a curveball at second five is not a curveball, it is
             * just a second problem statement.
             */
            @DefaultValue("90s") Duration curveballDelay,
            /**
             * Minimum characters written before the curveball may fire, on top
             * of the delay — an empty or barely-touched editor is not "mid-task".
             */
            @DefaultValue("40") int curveballMinChars) {
    }
}
