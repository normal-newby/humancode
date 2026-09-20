package com.example.humancode.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.humancode.interview.SessionState;
import com.example.humancode.problem.Problem;
import com.example.humancode.telemetry.Trigger;

/**
 * {@link PromptAssembler#hasChanged} is what {@link Interviewer} checks before
 * trusting a GOOD verdict — the fact the guard is built on, not just the prose
 * rendered into the prompt tail. See {@link CodeDiffTest} for the diff
 * algorithm itself.
 */
class PromptAssemblerDiffGuardTest {

    private static SessionState newState() {
        Problem problem = new Problem("scratch", "Scratch", "easy", List.of(), "statement",
                List.of(new Problem.ProblemFile("app.js", "javascript",
                        "function twoSum(nums) {\n}", "REFERENCE")),
                List.of("does the thing"), List.of("a curveball"), List.of());
        return new SessionState("scratch-session", problem, "javascript");
    }

    @Test
    @DisplayName("a fresh session with no edits has not changed")
    void freshSessionHasNoChange() {
        assertFalse(new PromptAssembler().hasChanged(newState()));
    }

    @Test
    @DisplayName("speaking with nothing typed since does not manufacture a change")
    void speakingAloneIsNotAChange() {
        SessionState state = newState();
        state.markCodeSpokenFor();
        assertFalse(new PromptAssembler().hasChanged(state));
    }

    @Test
    @DisplayName("a real edit is a change")
    void realEditIsAChange() {
        SessionState state = newState();
        state.code("app.js", "function twoSum(nums) {\n  const seen = new Map();\n}");
        assertTrue(new PromptAssembler().hasChanged(state));
    }

    @Test
    @DisplayName("once the interviewer speaks again, the baseline moves and the old edit stops counting")
    void baselineMovesAfterSpeaking() {
        SessionState state = newState();
        state.code("app.js", "function twoSum(nums) {\n  const seen = new Map();\n}");
        state.markCodeSpokenFor();
        assertFalse(new PromptAssembler().hasChanged(state));
    }

    @Test
    @DisplayName("the tail's own prose agrees with hasChanged")
    void inputTextAgreesWithHasChanged() {
        PromptAssembler prompts = new PromptAssembler();
        SessionState state = newState();
        Trigger idle = Trigger.of(Trigger.Kind.IDLE, "idle 30s", 10);

        assertFalse(prompts.hasChanged(state));
        assertTrue(prompts.input(state, idle).contains("(not one character has changed since you last spoke)"));

        state.code("app.js", "function twoSum(nums) {\n  const seen = new Map();\n}");

        assertTrue(prompts.hasChanged(state));
        assertTrue(prompts.input(state, idle).contains("```diff"));
    }
}
