package com.example.humancode.telemetry;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.interview.Phase;
import com.example.humancode.interview.SessionState;

/**
 * Decides <em>when</em> the interviewer speaks. Plain Java, no model calls.
 *
 * <p>This is the guard rail described in CLAUDE.md §2: keystrokes hit this class
 * many times a second and almost always produce nothing. Only when a rule fires
 * does anything reach OpenAI. If you find yourself wanting to call the model on
 * every edit, add a rule here instead.
 */
@Component
public class TriggerEngine {

    /** A paste this big with no typing behind it is worth commenting on. */
    private static final int PASTE_BURST_CHARS = 120;
    private static final double THRASH_RATIO = 1.5;
    private static final long THRASH_MIN_CHARS = 200;

    private final HumancodeProperties props;

    public TriggerEngine(HumancodeProperties props) {
        this.props = props;
    }

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

        // One-shot: they have been handed a problem and have not started.
        if (state.phase() == Phase.INTRO
                && state.elapsed().compareTo(idleThreshold) > 0
                && state.fireOnce("no-start")) {
            return Optional.of(Trigger.of(Trigger.Kind.NO_START,
                    "Candidate has not typed a single character since the problem was delivered %d seconds ago."
                            .formatted(state.elapsed().toSeconds()),
                    15));
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

    public Optional<Trigger> onRun(SessionState state, boolean passed, String summary) {
        if (passed) {
            return Optional.of(Trigger.immediate(Trigger.Kind.TESTS_PASSED,
                    "All tests passed on run %d. %s".formatted(state.runCount(), summary),
                    -20));
        }
        return Optional.of(Trigger.immediate(Trigger.Kind.TESTS_FAILED,
                "Run %d failed. %s".formatted(state.runCount(), summary),
                10));
    }

    /** Whether enough time has passed since the last line for another one. */
    public boolean cooledDown(SessionState state) {
        return state.sinceLastUtterance().compareTo(props.interview().quipCooldown()) >= 0;
    }
}
