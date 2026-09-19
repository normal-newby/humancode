package com.example.humancode.telemetry;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.interview.Phase;
import com.example.humancode.interview.SessionState;

import lombok.RequiredArgsConstructor;

/**
 * Decides <em>when</em> the interviewer speaks. Plain Java, no model calls.
 *
 * <p>This is the guard rail described in CLAUDE.md §2: keystrokes hit this class
 * many times a second and almost always produce nothing. Only when a rule fires
 * does anything reach OpenAI. If you find yourself wanting to call the model on
 * every edit, add a rule here instead.
 */
@RequiredArgsConstructor
@Component
public class TriggerEngine {

    /** A paste this big with no typing behind it is worth commenting on. */
    private static final int PASTE_BURST_CHARS = 120;
    private static final int FIRST_IMPLEMENTATION_CHARS = 12;
    private static final int SUBSTANTIAL_EDIT_CHARS = 160;
    private static final int HEAVY_DELETE_CHARS = 80;
    private static final double THRASH_RATIO = 1.5;
    private static final long THRASH_MIN_CHARS = 200;

    private final HumancodeProperties props;

    /**
     * Evaluate every rule against current state.
     *
     * @return the trigger that should fire, or empty — which is the common case
     */
    public Optional<Trigger> evaluate(SessionState state) {
        if (state.phase() == Phase.DONE || state.phase() == Phase.REPORT) {
            return Optional.empty();
        }

        Duration idleThreshold = props.interview().idleThreshold();

        // Re-arm every threshold-length window. An untouched editor should not
        // receive one opening jab and then an hour of silence.
        if (state.phase() == Phase.INTRO && state.elapsed().compareTo(idleThreshold) > 0) {
            long seconds = state.elapsed().toSeconds();
            String key = "no-start-" + (seconds / Math.max(1, idleThreshold.toSeconds()));
            if (state.fireOnce(key)) {
                return Optional.of(Trigger.of(Trigger.Kind.NO_START,
                        "Candidate has not typed a single character since the problem was delivered %d seconds ago."
                                .formatted(seconds),
                        15));
            }
        }

        // Thrashing: deleting much more than writing.
        if (state.charsInserted() > THRASH_MIN_CHARS
                && state.deleteRatio() > THRASH_RATIO
                && state.fireOnce("thrash")) {
            return Optional.of(Trigger.of(Trigger.Kind.MASS_DELETION,
                    "Candidate has deleted %d characters against %d written (ratio %.1f) — they keep starting over."
                            .formatted(state.charsDeleted(), state.charsInserted(), state.deleteRatio()),
                    20));
        }

        // Idle: the bread-and-butter trigger.
        if (state.phase() == Phase.CODING && state.idleFor().compareTo(idleThreshold) > 0) {
            long seconds = state.idleFor().toSeconds();
            // Re-arm every threshold-length window so a long silence escalates.
            String key = "idle-" + (seconds / Math.max(1, idleThreshold.toSeconds()));
            if (state.fireOnce(key)) {
                return Optional.of(Trigger.of(Trigger.Kind.IDLE,
                        "Candidate has not typed for %d seconds.".formatted(seconds),
                        12));
            }
        }

        // Long session, little code.
        if (state.elapsed().toMinutes() >= 5
                && state.charsInserted() < 120
                && state.fireOnce("slow-progress")) {
            return Optional.of(Trigger.of(Trigger.Kind.SLOW_PROGRESS,
                    "Five minutes in and only %d characters written.".formatted(state.charsInserted()),
                    18));
        }

        return Optional.empty();
    }

    /**
     * Paste is evaluated on ingest rather than on the timer — the interviewer
     * noticing your paste <em>as it happens</em> is the whole joke.
     */
    public Optional<Trigger> onPaste(SessionState state, long chars) {
        if (chars < PASTE_BURST_CHARS) {
            return Optional.empty();
        }
        return Optional.of(Trigger.immediate(Trigger.Kind.PASTE_BURST,
                "Candidate just pasted %d characters in one go.".formatted(chars),
                25));
    }

    /**
     * React to completed pieces of work rather than individual keystrokes.
     * Telemetry arrives in 1.5-second batches, and {@link Trigger#of} applies
     * the shared cooldown before a model call is made. That keeps this lively
     * without turning a fast typist into an API bill.
     */
    public Optional<Trigger> onMeaningfulEdit(SessionState state, long inserted, long deleted,
            int completedLines) {
        if (state.phase() != Phase.CODING) {
            return Optional.empty();
        }

        if (deleted >= HEAVY_DELETE_CHARS && deleted > inserted * 2L) {
            return Optional.of(Trigger.of(Trigger.Kind.HEAVY_DELETE,
                    "Candidate just deleted %d characters while adding only %d."
                            .formatted(deleted, inserted),
                    16));
        }

        if (state.charsInserted() >= FIRST_IMPLEMENTATION_CHARS && state.fireOnce("first-implementation")) {
            return Optional.of(Trigger.immediate(Trigger.Kind.FIRST_IMPLEMENTATION,
                    "Candidate has started their implementation (%d characters written so far)."
                            .formatted(state.charsInserted()),
                    0));
        }

        if (completedLines > 0) {
            return Optional.of(Trigger.of(Trigger.Kind.LINE_COMPLETED,
                    "Candidate completed %d line%s of code."
                            .formatted(completedLines, completedLines == 1 ? "" : "s"),
                    0));
        }

        if (inserted >= SUBSTANTIAL_EDIT_CHARS) {
            return Optional.of(Trigger.of(Trigger.Kind.SUBSTANTIAL_EDIT,
                    "Candidate added %d characters in one editor batch.".formatted(inserted),
                    4));
        }

        return Optional.empty();
    }

    public Optional<Trigger> onRun(SessionState state, boolean passed, String summary) {
        if (passed) {
            return Optional.of(Trigger.of(Trigger.Kind.TESTS_PASSED,
                    "All tests passed on run %d. %s".formatted(state.runCount(), summary),
                    -20));
        }
        return Optional.of(Trigger.of(Trigger.Kind.TESTS_FAILED,
                "Run %d failed. %s".formatted(state.runCount(), summary),
                10));
    }

    /** Whether enough time has passed since the last line for another one. */
    public boolean cooledDown(SessionState state) {
        return state.sinceLastUtterance().compareTo(props.interview().quipCooldown()) >= 0;
    }
}
