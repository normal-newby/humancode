package com.example.humancode.problem;

import java.util.Locale;
import java.util.Optional;

/**
 * What the candidate asked for.
 *
 * <p>Problems carry their difficulty as a plain lowercase string — the bank
 * files and the model's structured output both do — so this exists to parse
 * that leniently in one place rather than comparing strings at four call sites.
 */
public enum Difficulty {
    /**
     * Below easy on purpose: one thing to write, no rules to hold in your head.
     * It exists so a candidate can meet the interviewer without also meeting a
     * problem — the joke needs about four minutes of code to play out, not
     * fifteen.
     */
    VERY_EASY,
    EASY,
    MEDIUM,
    HARD;

    /** The lowercase form used in problem JSON, the API and the UI. */
    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * @return empty for null, blank or anything unrecognised — which the
     *         sources read as "any difficulty will do", the behaviour from
     *         before this existed
     */
    public static Optional<Difficulty> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        // `very-easy` on the wire and in problem JSON, `very easy` if a hand-written
        // file spells it out, VERY_EASY in Java. One level with two words in it is
        // not worth a second parser.
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Optional.of(valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** True when {@code problem} is the difficulty this asks for. */
    public boolean matches(Problem problem) {
        return problem != null && parse(problem.difficulty()).filter(this::equals).isPresent();
    }
}
