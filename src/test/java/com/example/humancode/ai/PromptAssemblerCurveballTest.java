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
 * A curveball changes the requirement mid-session, well after {@link
 * PromptAssembler#instructions} was cached with the original rubric — this
 * pins that the amendment still reaches both the quip tail and the report
 * card tail once {@code SessionState.recordCurveball} has been called, which
 * is what {@code InterviewDirector.fire()} does for a delivered CURVEBALL
 * trigger.
 */
class PromptAssemblerCurveballTest {

    private static SessionState newState() {
        Problem problem = new Problem("scratch", "Scratch", "easy", List.of(), "sort the list numerically",
                List.of(new Problem.ProblemFile("app.js", "javascript", "// your code here", "REFERENCE")),
                List.of("sorts numerically"), List.of("now sort alphabetically instead"), List.of());
        return new SessionState("scratch-session", problem, "javascript");
    }

    @Test
    @DisplayName("no curveball fired yet: neither tail mentions a scope change")
    void noCurveballYet() {
        PromptAssembler prompts = new PromptAssembler();
        SessionState state = newState();

        assertFalse(prompts.input(state, Trigger.of(Trigger.Kind.IDLE, "idle", 10)).contains("Scope changed"));
        assertFalse(prompts.reportInput(state).contains("Scope changed"));
    }

    @Test
    @DisplayName("a delivered curveball reaches the quip tail")
    void curveballReachesQuipTail() {
        PromptAssembler prompts = new PromptAssembler();
        SessionState state = newState();

        state.recordCurveball("now sort alphabetically instead");

        String tail = prompts.input(state, Trigger.of(Trigger.Kind.IDLE, "idle", 10));
        assertTrue(tail.contains("Scope changed mid-session"), tail);
        assertTrue(tail.contains("now sort alphabetically instead"), tail);
    }

    @Test
    @DisplayName("a delivered curveball reaches the report card tail")
    void curveballReachesReportTail() {
        PromptAssembler prompts = new PromptAssembler();
        SessionState state = newState();

        state.recordCurveball("now sort alphabetically instead");

        String tail = prompts.reportInput(state);
        assertTrue(tail.contains("Scope changed mid-session"), tail);
        assertTrue(tail.contains("now sort alphabetically instead"), tail);
        assertTrue(tail.contains("it wins"), "must tell the model the curveball wins on conflict");
    }

    @Test
    @DisplayName("recordCurveball is append-only, in order, for however many fire")
    void multipleCurveballsAllAppear() {
        PromptAssembler prompts = new PromptAssembler();
        SessionState state = newState();

        state.recordCurveball("now sort alphabetically instead");
        state.recordCurveball("now show a running count too");

        String tail = prompts.input(state, Trigger.of(Trigger.Kind.IDLE, "idle", 10));
        assertTrue(tail.contains("now sort alphabetically instead"), tail);
        assertTrue(tail.contains("now show a running count too"), tail);
        assertTrue(tail.indexOf("alphabetically") < tail.indexOf("running count"), "must preserve delivery order");
    }
}
