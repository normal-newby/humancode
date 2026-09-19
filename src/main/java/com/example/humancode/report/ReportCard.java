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
        /** True when the model was unavailable, failed, or got rejected by the guard. */
        boolean canned) {

    public record Stats(
            long elapsedSeconds,
            long charsWritten,
            long charsDeleted,
            int pasteCount,
            int runCount,
            int failedRunCount,
            int finalImpatience,
            boolean testsEverPassed) {
    }
}
