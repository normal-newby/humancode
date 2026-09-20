package com.example.humancode.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything tunable about a session lives here rather than in code, so tone,
 * pacing and model choice can be changed without a recompile.
 */
@ConfigurationProperties(prefix = "humancode")
public record HumancodeProperties(Ai ai, Interview interview, Problems problems, Speech speech) {

    public record Ai(
            String apiKey,
            /** Deliberate path: problem statements, hints, curveballs, report card. */
            String model,
            /** Quip path: reactive one-liners. Fires often, must feel instant. */
            String quipModel,
            Duration requestTimeout,
            /**
             * Deadline for the one report-card call, applied per request.
             *
             * <p>It needs its own because it reasons: it has to check every
             * requirement against the finished files, which is the only
             * verification in the app (CLAUDE.md §6), and that runs past the
             * client-wide {@code requestTimeout}. Worse than slow, the
             * client-wide one does not fail a long call, it retries it — so the
             * short deadline buys nothing and costs three attempts. Same lesson
             * as {@code problems.generation-timeout}, same fix.
             */
            @DefaultValue("120s") Duration reportTimeout) {
    }

    /**
     * The interviewer's voice (ElevenLabs). Absent key means a silent app that
     * otherwise behaves identically — see {@code speech/SpeechService}.
     */
    public record Speech(
            String apiKey,
            /**
             * One voice for the whole session, whatever mood it is in — see
             * {@code Delivery}. Swap it here; there is no picker, because the
             * interviewer is one person.
             */
            @DefaultValue("nPczCjzI2devNBz1zQrb") String voiceId,
            /**
             * <b>Keep this on a v3 model unless you also remove the audio
             * tags.</b> They are what make the angry end actually shout, and a
             * model that does not interpret them reads "[shouting]" aloud.
             * {@code SpeechService} checks, but the check is a guard, not a
             * licence to point this somewhere else and hope.
             */
            @DefaultValue("eleven_v3") String model,
            /** Set false to run silent with a key present — cheaper demos, same app. */
            @DefaultValue("true") boolean enabled,
            /**
             * Deadline for one synthesis. Short on purpose: the line is already
             * on screen being typed out, and a late arrival is worse than none.
             */
            @DefaultValue("8s") Duration requestTimeout,
            /**
             * How many clips to hold at once. Each is tens of kilobytes and
             * lives only until the tab has played it, so this is a leak stop
             * rather than a cache anyone benefits from.
             */
            @DefaultValue("64") int maxClips) {
    }

    public record Problems(
            /** {@code bank} in dev, {@code generated} in prod. */
            @DefaultValue("bank") String source,
            /**
             * How many generated problems to keep warm <em>per difficulty</em>.
             *
             * <p>Only meaningful with {@code source=generated}. Per difficulty,
             * because the candidate picks one: a pool of three easy problems
             * cannot answer a request for a hard one. At the default of 1 that
             * is three warm problems and three calls on a first run, paid once
             * and then cached to disk. Zero disables pre-generation and restores
             * the blocking call in front of the begin button.
             */
            @DefaultValue("1") int poolSize,
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
