package com.example.humancode.user;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * What counts as a handle, in one place.
 *
 * <p>Handles are the whole identity here — there is no email, no password and
 * nothing to recover — so the rules are deliberately narrow: lowercase, the
 * characters a shell prompt would not argue with, and short enough that a
 * leaderboard row stays one line at 84ch.
 */
public final class Handles {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 16;

    private static final Pattern SHAPE = Pattern.compile("[a-z0-9_-]+");

    /**
     * Words that would read as the app talking rather than as a candidate, and
     * would make a leaderboard row a lie. Cheap to hold, expensive to explain
     * once someone has claimed {@code admin}.
     */
    private static final Set<String> RESERVED = Set.of(
            "you", "me", "admin", "root", "system", "gpdetox", "codex", "openai", "anonymous", "null");

    private Handles() {
    }

    /**
     * Trims and lowercases. The candidate types whatever they like; exactly one
     * spelling of it reaches the database, so {@code Nimo} and {@code nimo}
     * cannot become two rows on the same leaderboard.
     */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    /** @throws InvalidHandleException with a sentence the UI can show verbatim */
    public static String require(String raw) {
        String handle = normalize(raw);
        if (handle.length() < MIN_LENGTH || handle.length() > MAX_LENGTH) {
            throw new InvalidHandleException(
                    "a handle is " + MIN_LENGTH + " to " + MAX_LENGTH + " characters");
        }
        if (!SHAPE.matcher(handle).matches()) {
            throw new InvalidHandleException("letters, numbers, dashes and underscores only");
        }
        if (RESERVED.contains(handle)) {
            throw new InvalidHandleException("that one is taken by the house");
        }
        return handle;
    }

    /** A handle the candidate typed is not usable. 400, not 500. */
    public static class InvalidHandleException extends RuntimeException {
        public InvalidHandleException(String message) {
            super(message);
        }
    }
}
