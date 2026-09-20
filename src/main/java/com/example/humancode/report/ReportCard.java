package com.example.humancode.report;

import java.util.List;

/**
 * What the candidate is handed at the end of the session — CLAUDE.md's "verdict,
 * insults, begrudging compliments, similar problems". {@code verdict},
 * {@code insults} and {@code compliments} come from the model (or the canned
 * fallback); {@code similarProblems} and {@code stats} are read straight off
 * the problem and the session, no model call needed for either.
 */
public record ReportCard(
        String verdict,
        List<String> insults,
        List<String> compliments,
        List<String> similarProblems,
        Stats stats,
        /**
         * How this session moves the candidate's saved rating, -15 to 30 — unlike
         * everything else here, this is not scoped to one session. The client is
         * the one that persists it (no accounts to hang it on server-side) and
         * folds it into the running total shown in the corner of every screen.
         */
        int ratingDelta,
        /**
         * Where the browser fetches the verdict read aloud, or null when the
         * app is running silent. It is an id rather than audio because the clip
         * is still being synthesised when this record is built — see
         * {@code speech/SpeechService}.
         */
        String speechId,
        /** True when the model was unavailable, failed, or got rejected by the guard. */
        boolean canned) {

    public record Stats(
            long elapsedSeconds,
            long charsWritten,
            long charsDeleted,
            int pasteCount,
            int submitCount,
            int finalImpatience) {
    }
}
