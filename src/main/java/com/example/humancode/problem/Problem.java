package com.example.humancode.problem;

import java.util.List;

/**
 * A single interview question, from the curated bank or generated at runtime.
 *
 * <p>{@code referenceSolution}, {@code rubric} and {@code followUps} are for the
 * interviewer's eyes only — they go into the cached prompt prefix and are
 * stripped before the problem reaches the browser. See {@link #forCandidate()}.
 */
public record Problem(
        String id,
        String title,
        String difficulty,
        List<String> tags,
        String statement,
        List<Example> examples,
        String starterCode,
        /** Function the test runner calls, e.g. {@code twoSum}. */
        String entryPoint,
        List<TestCase> tests,
        /** {@code exact} (default) or {@code unordered} for order-insensitive results. */
        String match,
        String referenceSolution,
        String optimalComplexity,
        List<String> rubric,
        List<String> followUps,
        List<String> similarProblems) {

    public record Example(String input, String output, String explanation) {
    }

    public Problem {
        if (match == null || match.isBlank()) {
            match = "exact";
        }
        if (tests == null) {
            tests = List.of();
        }
    }

    /**
     * The subset the candidate is allowed to see. Tests and entry point are
     * included because the browser runs them; see {@link TestCase}.
     */
    public Problem forCandidate() {
        return new Problem(id, title, difficulty, tags, statement, examples, starterCode,
                entryPoint, tests, match,
                null, null, List.of(), List.of(), List.of());
    }
}
