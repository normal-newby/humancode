package com.example.humancode.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything tunable about a session lives here rather than in code, so tone,
 * pacing and model choice can be changed without a recompile.
 */
@ConfigurationProperties(prefix = "humancode")
public record HumancodeProperties(Ai ai, Interview interview) {

    public record Ai(
            String apiKey,
            /** Deliberate path: problem statements, hints, curveballs, report card. */
            String model,
            /** Quip path: reactive one-liners. Fires often, must feel instant. */
            String quipModel,
            Duration requestTimeout) {
    }

    public record Interview(
            /** Silence longer than this counts as idle and arms the idle trigger. */
            Duration idleThreshold,
            /** Hard floor between two interviewer utterances. Protects the joke. */
            Duration quipCooldown,
            Duration telemetryBatchWindow,
            String defaultPersona) {
    }
}
