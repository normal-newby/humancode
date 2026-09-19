package com.example.humancode.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.config.OpenAiClientHolder;
import com.example.humancode.problem.GeneratedProblem.GeneratedTest;

import tools.jackson.databind.ObjectMapper;

/**
 * Validation of what the model sends back.
 *
 * <p>Every rejection here costs a 40-90 second call and produces nothing, so a
 * check that is too strict is not "safe" — it is an outage that looks like a
 * quiet retry. One of these was exactly that; see {@link #starterMayBeLongerThanTheAnswer}.
 */
class ProblemGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProblemGenerator generator = new ProblemGenerator(
            new OpenAiClientHolder(null),
            new HumancodeProperties(
                    new HumancodeProperties.Ai("", "gpt-5", "gpt-5-mini", Duration.ofSeconds(30)),
                    new HumancodeProperties.Interview(
                            Duration.ofSeconds(20), Duration.ofSeconds(8), Duration.ofMillis(1500)),
                    new HumancodeProperties.Problems("generated", 1, Duration.ofSeconds(180), "")),
            mapper);

    @Test
    @DisplayName("a JSDoc starter longer than a terse solution is still a valid problem")
    void starterMayBeLongerThanTheAnswer() {
        // The original check compared lengths, and rejected this: an easy
        // problem's starter carries a comment block while its answer is one
        // line. Every easy generation failed, silently, and cost a full call.
        String starter = """
                /**
                 * @param {number[]} nums
                 * @return {number}
                 */
                function total(nums) {
                  // your code here
                }
                """;
        String solution = "function total(nums) { return nums.reduce((a, b) => a + b, 0); }";
        assertTrue(starter.length() > solution.length(), "the fixture must reproduce the shape");

        Problem problem = generator.convert(generated(starter, solution), Difficulty.EASY);

        assertEquals("easy", problem.difficulty());
        assertEquals("total", problem.entryPoint());
    }

    @Test
    @DisplayName("a starter that already returns something is the answer, and is rejected")
    void rejectsAStarterThatIsTheAnswer() {
        String starter = """
                /**
                 * @param {number[]} nums
                 * @return {number}
                 */
                function total(nums) {
                  return nums.reduce((a, b) => a + b, 0);
                }
                """;

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> generator.convert(generated(starter, starter), Difficulty.EASY));
        assertTrue(e.getMessage().contains("return"), e.getMessage());
    }

    @Test
    @DisplayName("the requested difficulty wins over the label the model chose")
    void requestedDifficultyWins() {
        GeneratedProblem g = generated("function total(nums) {\n  // your code here\n}",
                "function total(nums) { return 1; }");

        // The fixture says EASY; the candidate asked for HARD.
        assertEquals("hard", generator.convert(g, Difficulty.HARD).difficulty());
    }

    @Test
    @DisplayName("a duplicated test case is a test the model did not write")
    void rejectsDuplicateTests() {
        GeneratedProblem g = new GeneratedProblem(
                "Total", GeneratedProblem.Difficulty.EASY, List.of("array"), "Add them up.",
                "function total(nums) {\n  // your code here\n}", "total",
                List.of(new GeneratedTest("[[1,2]]", "3"),
                        new GeneratedTest("[[1,2]]", "3"),
                        new GeneratedTest("[[]]", "0")),
                GeneratedProblem.Match.EXACT, "function total(nums) { return 1; }", "O(n)",
                List.of("adds"), List.of("what if empty"), List.of("sum"));

        assertThrows(IllegalStateException.class, () -> generator.convert(g, Difficulty.EASY));
    }

    @Test
    @DisplayName("two tests is not enough to tell a right answer from a lucky one")
    void rejectsTooFewTests() {
        GeneratedProblem g = new GeneratedProblem(
                "Total", GeneratedProblem.Difficulty.EASY, List.of("array"), "Add them up.",
                "function total(nums) {\n  // your code here\n}", "total",
                List.of(new GeneratedTest("[[1,2]]", "3"), new GeneratedTest("[[]]", "0")),
                GeneratedProblem.Match.EXACT, "function total(nums) { return 1; }", "O(n)",
                List.of("adds"), List.of("what if empty"), List.of("sum"));

        assertThrows(IllegalStateException.class, () -> generator.convert(g, Difficulty.EASY));
    }

    private GeneratedProblem generated(String starter, String solution) {
        return new GeneratedProblem(
                "Total",
                GeneratedProblem.Difficulty.EASY,
                List.of("array"),
                "Add up the numbers.",
                starter,
                "total",
                List.of(new GeneratedTest("[[1,2,3]]", "6"),
                        new GeneratedTest("[[]]", "0"),
                        new GeneratedTest("[[-1,1]]", "0")),
                GeneratedProblem.Match.EXACT,
                solution,
                "O(n) time",
                List.of("one pass"),
                List.of("what if it overflows"),
                List.of("running sum"));
    }
}
