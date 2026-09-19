package com.example.humancode.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Difficulty arrives as a lowercase string from three directions — the bank
 * JSON, the model's structured output, and the browser — so the parsing has to
 * be forgiving in exactly one place.
 */
class DifficultyTest {

    @Test
    @DisplayName("the lowercase labels the UI and the JSON use round trip")
    void parsesLabels() {
        assertEquals(Optional.of(Difficulty.EASY), Difficulty.parse("easy"));
        assertEquals(Optional.of(Difficulty.MEDIUM), Difficulty.parse("MEDIUM"));
        assertEquals(Optional.of(Difficulty.HARD), Difficulty.parse(" Hard "));
        assertEquals("hard", Difficulty.HARD.label());
    }

    @Test
    @DisplayName("anything unrecognised means 'any', not an error")
    void unknownIsEmpty() {
        // The API takes this straight from a request body. A typo must start a
        // session with whatever is warm, never fail the request.
        assertEquals(Optional.empty(), Difficulty.parse(null));
        assertEquals(Optional.empty(), Difficulty.parse(""));
        assertEquals(Optional.empty(), Difficulty.parse("impossible"));
    }

    @Test
    @DisplayName("matching compares against the problem's own label")
    void matchesProblems() {
        Problem hard = problem("hard");

        assertTrue(Difficulty.HARD.matches(hard));
        assertFalse(Difficulty.EASY.matches(hard));
        assertFalse(Difficulty.HARD.matches(problem("nonsense")));
        assertFalse(Difficulty.HARD.matches(null));
    }

    private Problem problem(String difficulty) {
        return new Problem("p", "P", difficulty, java.util.List.of(), "statement", java.util.List.of(),
                "function p() {}", "p", java.util.List.of(new TestCase(java.util.List.of(), 1)),
                "exact", "function p() { return 1; }", "O(1)", java.util.List.of(),
                java.util.List.of(), java.util.List.of());
    }
}
