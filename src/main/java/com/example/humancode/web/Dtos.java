package com.example.humancode.web;

import java.util.List;

import com.example.humancode.interview.Utterance;
import com.example.humancode.problem.Problem;
import com.example.humancode.telemetry.EventType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Request and response shapes for the REST surface. */
public final class Dtos {

    private Dtos() {
    }

    public record StartSessionRequest(String problemId, String language) {
    }

    public record SessionResponse(
            String sessionId,
            Problem problem,
            String language,
            String phase,
            int impatience,
            List<Utterance> transcript,
            List<String> notes,
            boolean live) {
    }

    /**
     * One batch of editor telemetry. The client buffers for ~1.5s and sends
     * these in bulk — never one request per keystroke.
     */
    public record TelemetryBatch(
            @NotNull List<TelemetryItem> events,
            /** Full editor contents at the end of the batch. */
            String code) {
    }

    public record TelemetryItem(
            @NotNull EventType type,
            @PositiveOrZero long inserted,
            @PositiveOrZero long deleted,
            String detail) {
    }

    public record RunResultRequest(
            boolean passed,
            @PositiveOrZero int passedCount,
            @PositiveOrZero int failedCount,
            String firstFailure,
            @PositiveOrZero long durationMs) {

        public String summary() {
            if (passed) {
                return "%d/%d assertions passed.".formatted(passedCount, passedCount + failedCount);
            }
            return "%d passed, %d failed. First failure: %s"
                    .formatted(passedCount, failedCount,
                            firstFailure == null ? "(not reported)" : firstFailure);
        }
    }

    public record MetricsResponse(
            long elapsedSeconds,
            long idleSeconds,
            long charsInserted,
            long charsDeleted,
            double deleteRatio,
            int pasteCount,
            int runCount,
            int failedRunCount,
            int impatience) {
    }
}
