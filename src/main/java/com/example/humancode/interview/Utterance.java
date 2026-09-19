package com.example.humancode.interview;

import java.time.Instant;

/**
 * One thing the interviewer said, plus why it said it.
 *
 * <p>{@code trigger} is the important field: when the interviewer says something
 * strange mid-demo, this is how you find out which rule fired.
 */
public record Utterance(
        String id,
        Instant at,
        String trigger,
        String line,
        String mood,
        int impatienceAfter,
        boolean canned) {
}
