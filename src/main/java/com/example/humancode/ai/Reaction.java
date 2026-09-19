package com.example.humancode.ai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured shape the interviewer replies in. Never parse prose — the SDK
 * derives a strict JSON schema from this record and the model is constrained to
 * it, so {@code line} arrives clean and {@code impatienceDelta} is a real number
 * rather than something scraped out of a sentence.
 */
@JsonClassDescription("A single short reaction from the interviewer, in character.")
public record Reaction(

        @JsonPropertyDescription("""
                Your judgement of the code in front of you, decided before you write anything. \
                GOOD when the change moves toward a working answer, WRONG when it is broken, \
                misses what was asked, or undoes progress, NEUTRAL when the code did not \
                meaningfully change. Judge the code, never the pace.""")
        Verdict verdict,

        @JsonPropertyDescription("""
                What the interviewer says out loud. One plain sentence of 3 to 12 words, \
                maximum 120 characters. Dry and specific to the visible code or clock. \
                Never give code, solution steps, algorithms, data structures, test advice, \
                or a next action. Do not use em dashes, en dashes, semicolons, colons, \
                ellipses, markdown, or lists.""")
        String line,

        @JsonPropertyDescription("The interviewer's current mood, which drives the avatar and the meter.")
        Mood mood,

        @JsonPropertyDescription("""
                How much this moment should move the impatience meter, from -20 to 25. \
                It follows the verdict: positive on WRONG, negative on GOOD, small either \
                way on NEUTRAL. Size it by how wrong or how good, not by how long they took.""")
        int impatienceDelta,

        @JsonPropertyDescription("""
                A short third-person observation for the interviewer's private notes \
                panel, e.g. 'Reached for a nested loop. Predictable.' Under 90 characters.""")
        String note) {

    /**
     * The meter follows the judgement, whatever number came back with it.
     *
     * <p>Structured output puts {@code verdict} first so the model commits to a
     * judgement before it writes a sentence (the same ordering trick as
     * {@code GeneratedReport.outcome}), but nothing stops it then returning
     * {@code GOOD} with a cheerfully punitive {@code +8}. When the two
     * disagree the judgement wins: broken code costs them, work that lands
     * earns some back, and the candidate can read the meter as a verdict on
     * the code rather than on the clock.
     *
     * <p>It corrects the sign only. How much this moment is worth is still the
     * model's call, and a {@code NEUTRAL} is passed through untouched.
     */
    public int alignedDelta() {
        return switch (verdict == null ? Verdict.NEUTRAL : verdict) {
            case GOOD -> Math.min(impatienceDelta, -1);
            case WRONG -> Math.max(impatienceDelta, 1);
            case NEUTRAL -> impatienceDelta;
        };
    }

    /** What the interviewer makes of the code itself, as opposed to the candidate. */
    public enum Verdict {
        GOOD,
        NEUTRAL,
        WRONG
    }

    public enum Mood {
        NEUTRAL,
        AMUSED,
        IMPATIENT,
        EXASPERATED,
        IMPRESSED
    }
}
