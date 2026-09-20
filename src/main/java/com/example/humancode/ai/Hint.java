package com.example.humancode.ai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A single hint, given only because the candidate asked for one directly.
 * Never parse prose — same reasoning as {@link Reaction}: the SDK derives a
 * strict JSON schema from this record.
 */
@JsonClassDescription("One hint, given on request, not a reaction to what just changed.")
public record Hint(

        @JsonPropertyDescription("""
                One real nudge, one to two plain sentences. Point at the file, the function, \
                or the requirement they have not satisfied yet, or ask the question a good \
                mentor asks to get someone looking in the right place. Never literal code, \
                never a value to paste in, never the exact fix spelled out end to end, never \
                the name of a specific algorithm or data structure to use. No markdown, no \
                code fences, no lists.""")
        String text) {
}
