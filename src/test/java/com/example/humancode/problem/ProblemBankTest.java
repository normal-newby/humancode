package com.example.humancode.problem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Every bank problem must give the candidate something to actually build. A
 * problem where every file's starter already matches its answer looks fine on
 * screen and hands over a passing solution to stare at.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dev")
class ProblemBankTest {

    @Autowired
    private ProblemBank bank;

    @Test
    @DisplayName("the bank can serve every difficulty the UI offers")
    void coversEveryDifficulty() {
        // The dev profile runs on the bank alone. If a level is missing here,
        // choosing it silently hands the candidate a different one — see
        // ProblemBank.random(Difficulty).
        for (Difficulty difficulty : Difficulty.values()) {
            Problem chosen = bank.random(difficulty);
            assertTrue(difficulty.matches(chosen),
                    "no " + difficulty.label() + " problem in the bank; got " + chosen.id());
        }
    }

    @Test
    @DisplayName("every problem has files with a real gap between starter and reference content")
    void everyProblemHasWorkToDo() {
        assertFalse(bank.all().isEmpty(), "the bank must not be empty");

        for (Problem problem : bank.all()) {
            String where = "problem '" + problem.id() + "'";

            assertFalse(problem.files().isEmpty(), where + " has no files");
            assertFalse(problem.rubric().isEmpty(), where + " has no rubric");
            assertFalse(problem.curveballs().isEmpty(), where + " has no curveballs");

            boolean anyFileHasWork = problem.files().stream()
                    .anyMatch(f -> !f.starterContent().equals(f.referenceContent()));
            assertTrue(anyFileHasWork, where + " has no gap between any starter and reference content");

            for (Problem.ProblemFile file : problem.files()) {
                assertFalse(file.starterContent() == null, where + " file '" + file.name() + "' has no starter");
                assertFalse(file.referenceContent() == null || file.referenceContent().isBlank(),
                        where + " file '" + file.name() + "' has no reference content");
            }
        }
    }

    @Test
    @DisplayName("forCandidate strips reference content, rubric, curveballs and similar problems")
    void candidateViewHidesTheAnswer() {
        Problem full = bank.all().getFirst();
        Problem candidate = full.forCandidate();

        assertTrue(candidate.rubric().isEmpty(), "the rubric must never reach the browser");
        assertTrue(candidate.curveballs().isEmpty(), "curveballs must not leak ahead of time");
        assertTrue(candidate.similarProblems().isEmpty(), "similar problems must not leak");

        // The candidate still needs the starter code for every file to work from.
        assertFalse(candidate.files().isEmpty(), "files must reach the browser or there is nothing to edit");
        for (Problem.ProblemFile file : candidate.files()) {
            assertNull(file.referenceContent(), "the reference answer must never reach the browser");
        }
    }
}
