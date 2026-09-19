package com.example.humancode.telemetry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

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

        // Curveball: a pre-authored scope change, sprung once the candidate has
        // had genuine time to get somewhere. Text is trusted, problem-author
        // content — delivered verbatim by Interviewer, no model call involved.
        if (state.phase() == Phase.CODING
                && state.elapsed().compareTo(props.interview().curveballDelay()) > 0
                && state.charsInserted() >= props.interview().curveballMinChars()
                && !state.problem().curveballs().isEmpty()
                && state.fireOnce("curveball")) {
            List<String> options = state.problem().curveballs();
            String pick = options.get(ThreadLocalRandom.current().nextInt(options.size()));
            return Optional.of(Trigger.immediate(Trigger.Kind.CURVEBALL, pick, 5));
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
     *
     * <p><strong>Nothing here fires while the candidate is mid-line.</strong>
     * Telemetry arrives in 1.5-second batches, so a rule that only counts
     * characters fires against whatever fragment the timer caught — and then the
     * interviewer is reacting to {@code const total = arr.fil}, which is not a
     * decision to answer for, it is a person typing. {@link LineActivity#settled()}
     * is the gate: a newline means they committed to that line, deleted lines
     * mean they threw one away, and everything in between is still in progress.
     *
     * <p>Typing that never settles is not thereby immune. It is the idle rule's
     * job, on the director's timer — stopping mid-line for twenty seconds is a
     * thing worth asking about, and a half-written line they have abandoned is
     * fair game in a way that the same line still under their fingers is not.
     * {@code PromptAssembler} tells the model which of the two it is looking at.
     *
     * <p>{@link Trigger#of} still applies the shared cooldown on top, so a fast
     * typist crossing a line boundary every second does not become an API bill.
     */
    public Optional<Trigger> onMeaningfulEdit(SessionState state, long inserted, long deleted,
            LineActivity lines) {
        if (state.phase() != Phase.CODING) {
            return Optional.empty();
        }

        if (!lines.settled()) {
            return Optional.empty();
        }

        if (deleted >= HEAVY_DELETE_CHARS && deleted > inserted * 2L) {
            return Optional.of(Trigger.of(Trigger.Kind.HEAVY_DELETE,
                    "Candidate just deleted %d characters while adding only %d."
                            .formatted(deleted, inserted),
                    16));
        }

        // Gated on a settled buffer like everything else, which is what moved it
        // off "12 characters have appeared" — that always landed mid-identifier,
        // and being immediate it skipped the cooldown to do it. Now it lands on
        // the first line they actually finish, which is the beat it was after.
        if (state.charsInserted() >= FIRST_IMPLEMENTATION_CHARS && state.fireOnce("first-implementation")) {
            return Optional.of(Trigger.immediate(Trigger.Kind.FIRST_IMPLEMENTATION,
                    "Candidate has finished their first line of real code (%d characters written so far)."
                            .formatted(state.charsInserted()),
                    0));
        }

        if (lines.completed() > 0) {
            return Optional.of(Trigger.of(Trigger.Kind.LINE_COMPLETED,
                    "Candidate completed %d line%s of code."
                            .formatted(lines.completed(), lines.completed() == 1 ? "" : "s"),
                    0));
        }

        if (inserted >= SUBSTANTIAL_EDIT_CHARS) {
            return Optional.of(Trigger.of(Trigger.Kind.SUBSTANTIAL_EDIT,
                    "Candidate added %d characters in one editor batch.".formatted(inserted),
                    4));
        }

        return Optional.empty();
    }

    /**
     * The candidate handed the turn back. There is no automated verdict to
     * react to (CLAUDE.md §6) — the interviewer judges the current diff against
     * the rubric already sitting in the cached prompt prefix, the same way it
     * judges everything else.
     */
    public Optional<Trigger> onSubmit(SessionState state) {
        return Optional.of(Trigger.of(Trigger.Kind.SUBMITTED,
                "Candidate submitted (submission %d), %d characters written, %d seconds elapsed."
                        .formatted(state.submitCount(), state.charsInserted(), state.elapsed().toSeconds()),
                0));
    }

    /** Whether enough time has passed since the last line for another one. */
    public boolean cooledDown(SessionState state) {
        return state.sinceLastUtterance().compareTo(props.interview().quipCooldown()) >= 0;
    }
}
