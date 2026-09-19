package com.example.humancode.telemetry;

/**
 * A reason for the interviewer to speak.
 *
 * @param kind      which rule fired
 * @param detail    human-readable specifics, fed to the model and logged
 * @param urgency   0-100, how much this should move the impatience meter
 * @param cooldown  whether the global quip cooldown applies; false for events
 *                  the interviewer must react to immediately (a paste, a failed
 *                  run) regardless of how recently it spoke
 */
public record Trigger(Kind kind, String detail, int urgency, boolean cooldown) {

    public enum Kind {
        /** Session started and the candidate has not typed anything yet. */
        NO_START,
        /** Typing stopped for longer than the idle threshold. */
        IDLE,
        /** A large insert with no preceding keystrokes. */
        PASTE_BURST,
        /** The candidate has started writing a real implementation. */
        FIRST_IMPLEMENTATION,
        /** One or more lines were completed in the editor. */
        LINE_COMPLETED,
        /** A batch contains a substantial amount of new code. */
        SUBSTANTIAL_EDIT,
        /** A single edit batch threw away a meaningful amount of code. */
        HEAVY_DELETE,
        /** Deleting far more than they are writing — thrashing. */
        MASS_DELETION,
        /** Tests were run and failed. */
        TESTS_FAILED,
        /** Tests passed. Time to be begrudgingly positive. */
        TESTS_PASSED,
        /** Taking a long time with little to show for it. */
        SLOW_PROGRESS
    }

    public static Trigger of(Kind kind, String detail, int urgency) {
        return new Trigger(kind, detail, urgency, true);
    }

    public static Trigger immediate(Kind kind, String detail, int urgency) {
        return new Trigger(kind, detail, urgency, false);
    }
}
