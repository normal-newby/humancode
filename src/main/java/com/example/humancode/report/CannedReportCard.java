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
                    "You never submitted the work. The unfinished scaffolding stayed unfinished.",
                    List.of(
                            "Not one submission the entire session.",
                            "%d %s stayed untouched.".formatted(assessment.untouchedFiles(),
                                    assessment.untouchedFiles() == 1 ? "file" : "files"),
                            assessment.remainingGaps() + " scaffold gaps still remained."),
                    List.of());
        }

        if (assessment.remainingGaps() > 0) {
            return new GeneratedReport(
                    "You submitted with %d of %d scaffold gaps still open. That confidence was doing heavy lifting."
                            .formatted(assessment.remainingGaps(), assessment.totalGaps()),
                    List.of(
                            "The unfinished scaffolding was still visible.",
                            "%d of %d files changed at all.".formatted(assessment.changedFiles(), assessment.totalFiles()),
                            "You submitted it %d times anyway.".formatted(state.submitCount())),
                    List.of());
        }

        if (assessment.untouchedFiles() > 0) {
            return new GeneratedReport(
                    "You changed %d of %d files and submitted the rest as starter code. Ambitious."
                            .formatted(assessment.changedFiles(), assessment.totalFiles()),
                    List.of(
                            "%d files stayed exactly as given.".formatted(assessment.untouchedFiles()),
                            "The brief had %d requirements.".formatted(problem.rubric().size()),
                            "You handed it over %d times.".formatted(state.submitCount())),
                    List.of());
        }

        return new GeneratedReport(
                "Every starter file changed, which is more than the fallback can prove. The rest needs judgment.",
                List.of("The model report was unavailable.", "You submitted it %d times.".formatted(state.submitCount())),
                List.of());
    }

    private static Assessment assess(SessionState state, Problem problem) {
        int changedFiles = 0;
        int untouchedFiles = 0;
        int totalGaps = 0;
        int remainingGaps = 0;

        for (Problem.ProblemFile file : problem.files()) {
            String starter = file.starterContent() == null ? "" : file.starterContent();
            String current = state.code(file.name()) == null ? "" : state.code(file.name());
            if (starter.equals(current)) {
                untouchedFiles++;
            } else {
                changedFiles++;
            }
            totalGaps += countGaps(starter);
            remainingGaps += countGaps(current);
        }
        return new Assessment(changedFiles, untouchedFiles, problem.files().size(), totalGaps, remainingGaps);
    }

    private static int countGaps(String code) {
        return (int) SCAFFOLD_GAP.matcher(code).results().count();
    }

    private record Assessment(int changedFiles, int untouchedFiles, int totalFiles, int totalGaps,
            int remainingGaps) {
    }
}
