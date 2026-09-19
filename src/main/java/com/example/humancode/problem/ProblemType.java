package com.example.humancode.problem;

import java.util.Locale;
import java.util.Optional;

/** The shape of work the candidate receives. */
public enum ProblemType {
    /** Start from a scaffold and build the missing behaviour. */
    BUILD,
    /** Start from a complete-looking app and find the behaviour that is wrong. */
    BUG_FIX;

    /** Lowercase label used by the start-screen selector. */
    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Null and unknown values preserve the older "surprise me" behaviour. */
    public static Optional<ProblemType> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
