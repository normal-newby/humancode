package com.example.humancode.report;

import java.util.List;
import java.util.regex.Pattern;

import com.example.humancode.interview.SessionState;
import com.example.humancode.problem.Problem;

/**
 * Fallback report used when no {@code OPENAI_API_KEY} is configured, the call
 * fails, or the model's own report gets rejected by {@link ReportCardGuard}.
 * Same reasoning as {@code CannedLines}: a report card that goes blank is a
 * worse demo than a generic one, and it has to hold the same voice or the
 * switch away from the model shows.
 *
 * <p>There is no executable pass/fail signal (CLAUDE.md §6), but the fallback
 * can still distinguish untouched starter files and unfinished scaffold gaps.
 * A model outage must not turn a visibly partial submission into empty praise.
 *
 * <p>What it deliberately cannot do is award {@code WORKS}. Every branch here
 * is reasoning from file contents and counters, which is enough to prove an app
 * is <em>not</em> finished and never enough to prove one is. So the best canned
 * outcome is a flat {@code PARTIAL} that says so, and the understated "Hmm."
 * the real path gives a working app is not something this path gets to fake.
 */
final class CannedReportCard {

    private static final Pattern SCAFFOLD_GAP = Pattern.compile(
            "(?i)(your code here|\\bTODO\\b|throw new Error|IMPLEMENT ME)");

    private CannedReportCard() {
    }

    static GeneratedReport forSession(SessionState state, Problem problem) {
        Assessment assessment = assess(state, problem);

        if (state.submitCount() == 0) {
            return new GeneratedReport(
                    GeneratedReport.Outcome.BROKEN,
                    "What is this? You never handed anything over, so I have no app to open.",
                    List.of(
                            "Not one submission the entire session.",
                            "%d %s stayed untouched.".formatted(assessment.untouchedFiles(),
                                    assessment.untouchedFiles() == 1 ? "file" : "files"),
                            "%d marked gaps still remained.".formatted(assessment.remainingGaps())),
                    List.of(),
                    28,
                    -15);
        }

        if (assessment.remainingGaps() > 0) {
            return new GeneratedReport(
                    GeneratedReport.Outcome.BROKEN,
                    "My app does not work. %d of %d marked gaps are still sitting open in it."
                            .formatted(assessment.remainingGaps(), assessment.totalGaps()),
                    List.of(
                            "Your unfinished scaffolding is in my app.",
                            "%d of %d files changed at all.".formatted(assessment.changedFiles(),
                                    assessment.totalFiles()),
                            "You submitted it %d times anyway.".formatted(state.submitCount())),
                    List.of(),
                    24,
                    -10);
        }

        if (assessment.untouchedFiles() > 0) {
            return new GeneratedReport(
                    GeneratedReport.Outcome.PARTIAL,
                    "So %d of %d files are exactly what I gave you. I get to finish my own app."
                            .formatted(assessment.untouchedFiles(), assessment.totalFiles()),
                    List.of(
                            "%d files came back exactly as given.".formatted(assessment.untouchedFiles()),
                            "I asked for %d things.".formatted(problem.rubric().size()),
                            "You handed it over %d times.".formatted(state.submitCount())),
                    List.of(),
                    12,
                    -3);
        }

        return new GeneratedReport(
                GeneratedReport.Outcome.PARTIAL,
                "Hmm. Every file has something of yours in it now. Whether my app works remains unproven.",
                List.of("You submitted it %d times.".formatted(state.submitCount()),
                        "I still have to review all of this."),
                List.of(),
                4,
                3);
    }

    private static Assessment assess(SessionState state, Problem problem) {
        int changedFiles = 0;
        int untouchedFiles = 0;
        int originalGaps = 0;
        int remainingGaps = 0;

        for (Problem.ProblemFile file : problem.files()) {
            String starter = file.starterContent() == null ? "" : file.starterContent();
            String current = state.code(file.name()) == null ? "" : state.code(file.name());
            if (starter.equals(current)) {
                untouchedFiles++;
            } else {
                changedFiles++;
            }
            originalGaps += countGaps(starter);
            remainingGaps += countGaps(current);
        }
        // A candidate can add a TODO of their own. Never produce "one of zero"
        // in that edge case.
        int totalGaps = Math.max(originalGaps, remainingGaps);
        return new Assessment(changedFiles, untouchedFiles, problem.files().size(), totalGaps, remainingGaps);
    }

    private static int countGaps(String code) {
        return (int) SCAFFOLD_GAP.matcher(code).results().count();
    }

    private record Assessment(int changedFiles, int untouchedFiles, int totalFiles, int totalGaps,
            int remainingGaps) {
    }
}
