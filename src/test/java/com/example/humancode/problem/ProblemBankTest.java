package com.example.humancode.problem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Every bank problem must be runnable by the browser worker. A problem missing
 * its entry point or tests looks fine on screen and then fails the moment
 * someone presses run.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class ProblemBankTest {

    @Autowired
    private ProblemBank bank;

    @Test
    @DisplayName("every problem is executable: entry point, tests, and a matching starter")
    void everyProblemIsRunnable() {
        assertFalse(bank.all().isEmpty(), "the bank must not be empty");

        for (Problem problem : bank.all()) {
            String where = "problem '" + problem.id() + "'";

            assertNotNull(problem.entryPoint(), where + " has no entryPoint");
            assertFalse(problem.entryPoint().isBlank(), where + " has a blank entryPoint");
            assertFalse(problem.tests().isEmpty(), where + " has no test cases");

            assertTrue(problem.starterCode().contains(problem.entryPoint()),
                    where + " starter code does not declare " + problem.entryPoint());
            assertTrue(problem.referenceSolution().contains(problem.entryPoint()),
                    where + " reference solution does not define " + problem.entryPoint());

            assertTrue("exact".equals(problem.match()) || "unordered".equals(problem.match()),
                    where + " has unknown match mode: " + problem.match());

            for (TestCase test : problem.tests()) {
                assertNotNull(test.args(), where + " has a test with null args");
            }
        }
    }

    @Test
    @DisplayName("forCandidate strips the solution and rubric but keeps the tests")
    void candidateViewHidesTheAnswer() {
        Problem full = bank.all().getFirst();
        Problem candidate = full.forCandidate();

        assertNull(candidate.referenceSolution(), "the reference solution must never reach the browser");
        assertNull(candidate.optimalComplexity(), "complexity hints must not leak");
        assertTrue(candidate.rubric().isEmpty(), "the rubric must never reach the browser");
        assertTrue(candidate.followUps().isEmpty(), "follow-ups must not leak");

        // The worker runs in the candidate's own browser, so it cannot execute
        // a test it was not given. Inputs and outputs are not the algorithm.
        assertFalse(candidate.tests().isEmpty(), "tests must reach the browser or nothing can run");
        assertNotNull(candidate.entryPoint());
    }
}
