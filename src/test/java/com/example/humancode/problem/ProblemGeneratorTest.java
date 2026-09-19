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
import com.example.humancode.problem.GeneratedProblem.GeneratedFile;

/**
 * Validation of what the model sends back.
 *
 * <p>Every rejection here costs a 40-90 second call and produces nothing, so a
 * check that is too strict is not "safe" — it is an outage that looks like a
 * quiet retry. One of these was exactly that; see {@link #aFileThatNeedsNoWorkIsFine}.
 */
class ProblemGeneratorTest {

    private final ProblemGenerator generator = new ProblemGenerator(
            new OpenAiClientHolder(null),
            new HumancodeProperties(
                    new HumancodeProperties.Ai("", "gpt-5", "gpt-5-mini", Duration.ofSeconds(30)),
                    new HumancodeProperties.Interview(
                            Duration.ofSeconds(20), Duration.ofSeconds(8), Duration.ofMillis(1500),
                            Duration.ofSeconds(90), 40),
                    new HumancodeProperties.Problems("generated", 1, Duration.ofSeconds(180), "")));

    @Test
    @DisplayName("a complete file alongside one with a real gap is a valid problem")
    void aFileThatNeedsNoWorkIsFine() {
        // The instructions explicitly allow a finished HTML shell, so the gap
        // check has to be "any file has work", not "every file has work". The
        // strict version rejects almost every generation, silently, at full
        // price — the same trap the old length-comparison check fell into.
        GeneratedProblem g = generated(
                file("index.html", "html", "<button id=\"go\">go</button>", "<button id=\"go\">go</button>"),
                file("app.js", "javascript", "// your code here", "document.getElementById('go');"));

        Problem problem = generator.convert(g, Difficulty.EASY);

        assertEquals(2, problem.files().size());
        assertEquals("easy", problem.difficulty());
    }

    @Test
    @DisplayName("a problem whose every file is already its own answer is rejected")
    void rejectsAProblemWithNothingToDo() {
        GeneratedProblem g = generated(
                file("index.html", "html", "<p>done</p>", "<p>done</p>"),
                file("app.js", "javascript", "const done = true;", "const done = true;"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> generator.convert(g, Difficulty.EASY));
        assertTrue(e.getMessage().contains("gap"), e.getMessage());
    }

    @Test
    @DisplayName("the requested difficulty wins over the label the model chose")
    void requestedDifficultyWins() {
        // The fixture says EASY; the candidate asked for HARD.
        assertEquals("hard", generator.convert(generated(), Difficulty.HARD).difficulty());
    }

    @Test
    @DisplayName("the requested bug-fix shape wins over a generated build label")
    void requestedProblemTypeWins() {
        assertEquals(ProblemType.BUG_FIX,
                generator.convert(generated(), Difficulty.EASY, ProblemType.BUG_FIX).type());
    }

    @Test
    @DisplayName("a problem with no files is rejected rather than served as an empty editor")
    void rejectsAProblemWithNoFiles() {
        GeneratedProblem g = new GeneratedProblem("Todo", GeneratedProblem.Difficulty.EASY,
                List.of("dom"), "Build a todo list.", List.of(),
                List.of("adds items", "removes items", "shows a count"),
                List.of("make the button yellow"), List.of("Notes App"));

        assertThrows(IllegalStateException.class, () -> generator.convert(g, Difficulty.EASY));
    }

    @Test
    @DisplayName("a problem with no curveball is rejected — the mechanism has nothing to spring")
    void rejectsAProblemWithNoCurveballs() {
        GeneratedProblem g = new GeneratedProblem("Todo", GeneratedProblem.Difficulty.EASY,
                List.of("dom"), "Build a todo list.",
                List.of(file("app.js", "javascript", "// your code here", "const x = 1;")),
                List.of("adds items", "removes items", "shows a count"),
                List.of(), List.of("Notes App"));

        assertThrows(IllegalStateException.class, () -> generator.convert(g, Difficulty.EASY));
    }

    private static GeneratedFile file(String name, String language, String starter, String reference) {
        return new GeneratedFile(name, language, starter, reference);
    }

    private GeneratedProblem generated(GeneratedFile... files) {
        List<GeneratedFile> list = files.length == 0
                ? List.of(file("app.js", "javascript", "// your code here", "const x = 1;"))
                : List.of(files);
        return new GeneratedProblem(
                "Todo",
                GeneratedProblem.Difficulty.EASY,
                List.of("dom"),
                "Build a todo list.",
                list,
                List.of("adds items", "removes items", "shows a count"),
                List.of("Actually, make the button yellow."),
                List.of("Notes App"));
    }
}
