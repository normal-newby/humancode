package com.example.humancode.problem;

import java.util.List;

/**
 * A single interview task, from the curated bank or generated at runtime — a
 * small app to build, not a pure function to complete. {@code files} is the
 * candidate's editor: however many files the task actually needs (an HTML/CSS/JS
 * scaffold for something visual, a single file for something simpler), decided
 * once when the problem is authored or generated, not recomputed mid-session.
 *
 * <p>{@code rubric}, {@code curveballs} and {@code similarProblems}, and every
 * file's {@code referenceContent}, are for the interviewer's eyes only — they go
 * into the cached prompt prefix and are stripped before the problem reaches the
 * browser. See {@link #forCandidate()}.
 */
public record Problem(
        String id,
        String title,
        String difficulty,
        List<String> tags,
        String statement,
        List<ProblemFile> files,
        List<String> rubric,
        /** Mid-task scope-change requests the interviewer springs on the candidate — see TriggerEngine. */
        List<String> curveballs,
        List<String> similarProblems) {

    /**
     * One file in the candidate's editor.
     *
     * @param language        a Monaco language id, e.g. {@code html}, {@code css}, {@code javascript}
     * @param starterContent  what the candidate sees when the session starts
     * @param referenceContent a correct, idiomatic answer — interviewer's eyes only
     */
    public record ProblemFile(String name, String language, String starterContent, String referenceContent) {
        public ProblemFile forCandidate() {
            return new ProblemFile(name, language, starterContent, null);
        }
    }

    public Problem {
        if (files == null) {
            files = List.of();
        }
        if (rubric == null) {
            rubric = List.of();
        }
        if (curveballs == null) {
            curveballs = List.of();
        }
        if (similarProblems == null) {
            similarProblems = List.of();
        }
    }

    /**
     * The subset the candidate is allowed to see: every file, minus each one's
     * reference answer, and none of the interviewer's judging material.
     */
    public Problem forCandidate() {
        return new Problem(id, title, difficulty, tags, statement,
                files.stream().map(ProblemFile::forCandidate).toList(),
                List.of(), List.of(), List.of());
    }
}
