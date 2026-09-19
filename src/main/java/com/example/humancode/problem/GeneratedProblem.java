package com.example.humancode.problem;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured shape the model returns when generating a problem.
 *
 * <p>Test arguments arrive as JSON <em>strings</em> rather than free-form
 * values: a strict JSON schema has no way to express "any JSON value", so the
 * model emits text we parse. Keeps the schema valid and the failure mode
 * obvious — a malformed literal is caught at parse time, not at demo time.
 *
 * <p>Jackson 2 annotations on purpose — that is what the OpenAI SDK's schema
 * generator reads. See CLAUDE.md §5.
 *
 * <p>No worked examples. The UI stopped showing them (UI-DESIGN.md §4.7) and
 * the prompt never carried them, so asking for them spent output tokens on
 * nothing — and output tokens are exactly what runs out and truncates the JSON
 * mid-field. Every field below has a live consumer.
 */
@JsonClassDescription("A complete coding-interview problem with executable JavaScript test cases.")
public record GeneratedProblem(

        @JsonPropertyDescription("Short problem title, e.g. 'Merge Intervals'.")
        String title,

        Difficulty difficulty,

        @JsonPropertyDescription("Three to five lowercase topic tags, e.g. 'array', 'hash-map'.")
        List<String> tags,

        @JsonPropertyDescription("""
                The problem statement as prose, 2-4 sentences. Plain text, no markdown \
                headings. State the constraints and what to return.""")
        String statement,

        @JsonPropertyDescription("""
                JavaScript starter code: a JSDoc comment followed by an empty function \
                declaration whose name is exactly entryPoint, containing only the \
                comment '// your code here'.""")
        String starterCode,

        @JsonPropertyDescription("Exact name of the function the tests call, e.g. 'twoSum'.")
        String entryPoint,

        @JsonPropertyDescription("""
                Six to eight test cases including edge cases (empty input, single element, \
                duplicates, negatives where meaningful).""")
        List<GeneratedTest> tests,

        @JsonPropertyDescription("""
                'unordered' only when the returned array's element order is genuinely \
                irrelevant; otherwise 'exact'.""")
        Match match,

        @JsonPropertyDescription("""
                A correct, idiomatic JavaScript solution defining exactly the function \
                named by entryPoint. It MUST pass every test case above.""")
        String referenceSolution,

        @JsonPropertyDescription("Optimal time and space complexity, with a one-line reason.")
        String optimalComplexity,

        @JsonPropertyDescription("Four to five things a strong answer does, for the interviewer to judge against.")
        List<String> rubric,

        @JsonPropertyDescription("Three follow-up questions to ask once the candidate passes.")
        List<String> followUps,

        @JsonPropertyDescription("Three similarly-shaped well-known problems.")
        List<String> similarProblems) {

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public enum Match {
        EXACT,
        UNORDERED
    }

    public record GeneratedTest(
            @JsonPropertyDescription("""
                    A JSON array of the arguments to spread into the function, e.g. \
                    '[[2,7,11,15], 9]'. Must parse as JSON.""")
            String argsJson,

            @JsonPropertyDescription("""
                    The expected return value as a JSON literal, e.g. '[0,1]' or 'true'. \
                    Must parse as JSON.""")
            String expectedJson) {
    }
}
