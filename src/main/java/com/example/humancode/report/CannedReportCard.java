package com.example.humancode.report;

import java.util.List;

import com.example.humancode.interview.SessionState;

/**
 * Fallback report used when no {@code OPENAI_API_KEY} is configured, the call
 * fails, or the model's own report gets rejected by {@link ReportCardGuard}.
 * Same reasoning as {@code CannedLines}: a report card that goes blank is a
 * worse demo than a generic one, and it has to hold the same voice or the
 * switch away from the model shows.
 *
 * <p>There is no pass/fail signal to branch on anymore (CLAUDE.md §6) — the
 * only two things this fallback actually knows are whether the candidate ever
 * submitted, and how the meter ended up.
 */
final class CannedReportCard {

    private CannedReportCard() {
    }

    static GeneratedReport forSession(SessionState state) {
        boolean neverSubmitted = state.submitCount() == 0;
        boolean furious = state.impatience() >= 70;

        String verdict = neverSubmitted
                ? "Never once handed it back. The clock ran out before you did."
                : furious
                        ? "Got there eventually, and it cost both of us something."
                        : "Handed it back a few times, which is at least a plan.";

        List<String> insults = neverSubmitted
                ? List.of(
                        "Not one submission the entire session.",
                        "A lot of typing, none of it declared finished.",
                        "You never once said you were done.")
                : List.of(
                        "That took a few tries to commit to.",
                        "The scenic route, evidently.",
                        "Submitted with real confidence, eventually.");

        List<String> compliments = neverSubmitted ? List.of() : List.of("You did eventually hand it over.");

        return new GeneratedReport(verdict, insults, compliments);
    }
}
