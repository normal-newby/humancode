package com.example.humancode.report;

import java.util.List;

import com.example.humancode.interview.SessionState;

/**
 * Fallback report used when no {@code OPENAI_API_KEY} is configured, the call
 * fails, or the model's own report gets rejected by {@link ReportCardGuard}.
 * Same reasoning as {@code CannedLines}: a report card that goes blank is a
 * worse demo than a generic one, and it has to hold the same voice or the
 * switch away from the model shows.
 */
final class CannedReportCard {

    private CannedReportCard() {
    }

    static GeneratedReport forSession(SessionState state) {
        boolean passed = state.testsEverPassed();
        boolean furious = state.impatience() >= 70;

        String verdict = passed
                ? "It passed, eventually. Getting there is not the same as getting there well."
                : furious
                        ? "It did not pass, and by the end neither of us was enjoying this."
                        : "It did not pass. The clock ran out before the code did.";

        List<String> insults = passed
                ? List.of(
                        "That took longer than it should have.",
                        "The scenic route, evidently.",
                        "Correct, after a great deal of drama.")
                : List.of(
                        "The tests were red the whole way through.",
                        "A lot of typing for very little working code.",
                        "This did not go the way you hoped.");

        List<String> compliments = passed ? List.of("You did eventually get there.") : List.of();

        return new GeneratedReport(verdict, insults, compliments);
    }
}
