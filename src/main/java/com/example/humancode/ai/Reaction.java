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
                What the interviewer says out loud. One or two sentences, maximum 200 \
                characters. In character, dry, and specific to what the candidate just \
                did. Never explain the solution.""")
        String line,

        @JsonPropertyDescription("The interviewer's current mood, which drives the avatar and the meter.")
        Mood mood,

        @JsonPropertyDescription("""
                How much this moment should move the impatience meter, from -20 to 25. \
                Negative when the candidate did something genuinely good.""")
        int impatienceDelta,

        @JsonPropertyDescription("""
                A short third-person observation for the interviewer's private notes \
                panel, e.g. 'Reached for a nested loop. Predictable.' Under 90 characters.""")
        String note) {

    public enum Mood {
        NEUTRAL,
        AMUSED,
        IMPATIENT,
        EXASPERATED,
        IMPRESSED
    }
}
