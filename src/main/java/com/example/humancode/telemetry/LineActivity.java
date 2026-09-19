package com.example.humancode.telemetry;

import java.util.Map;

/**
 * How many line boundaries an editor batch crossed, in each direction.
 *
 * <p>This exists to answer one question for {@link TriggerEngine}: has the
 * candidate <em>settled</em>, or are they still mid-line? Telemetry arrives in
 * 1.5-second batches, so without it every rule fires against whatever fragment
 * happened to be in the buffer when the timer went off — and the interviewer
 * reacts to {@code const total = arr.fil}, which is not a mistake, it is a
 * person typing. Reacting to that is the single fastest way to make the whole
 * thing feel like it is reading over your shoulder rather than watching your
 * work.
 *
 * @param completed newlines added, so lines the candidate committed to
 * @param removed   newlines deleted, so lines they threw away
 */
public record LineActivity(int completed, int removed) {

    public static final LineActivity NONE = new LineActivity(0, 0);

    /**
     * A moment worth reacting to. Pressing enter is the candidate saying they
     * are done with that line; deleting lines is them saying it decisively.
     * Anything else is typing in progress, and typing in progress is what the
     * idle timer is for.
     */
    public boolean settled() {
        return completed > 0 || removed > 0;
    }

    /**
     * Compares each file's newline count before and after the batch. Counting
     * newlines rather than diffing lines is deliberate: it is O(n) on a string
     * we already have, and "did a line boundary move" is the entire question.
     */
    public static LineActivity between(Map<String, String> previous, Map<String, String> current) {
        if (current == null) {
            return NONE;
        }
        int completed = 0;
        int removed = 0;
        for (Map.Entry<String, String> entry : current.entrySet()) {
            String before = previous == null ? null : previous.get(entry.getKey());
            if (before == null || entry.getValue() == null) {
                // A file the session has never seen contributes no delta. Its
                // starter newlines are not lines the candidate wrote.
                continue;
            }
            long delta = newlines(entry.getValue()) - newlines(before);
            if (delta > 0) {
                completed += Math.toIntExact(delta);
            } else {
                removed += Math.toIntExact(-delta);
            }
        }
        return new LineActivity(completed, removed);
    }

    private static long newlines(String code) {
        return code.chars().filter(character -> character == '\n').count();
    }
}
