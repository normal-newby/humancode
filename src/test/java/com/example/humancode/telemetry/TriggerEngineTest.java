package com.example.humancode.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.interview.Phase;
import com.example.humancode.interview.SessionState;
import com.example.humancode.problem.Problem;

/**
 * The trigger engine is the thing standing between the candidate's keyboard and
 * the API bill, so its rules are worth testing directly.
 */
class TriggerEngineTest {

    private static final HumancodeProperties PROPS = new HumancodeProperties(
            new HumancodeProperties.Ai("", "gpt-5", "gpt-5-mini", Duration.ofSeconds(30), Duration.ofSeconds(120)),
            new HumancodeProperties.Interview(
                    Duration.ofSeconds(20), Duration.ofSeconds(8), Duration.ofMillis(1500),
                    Duration.ofSeconds(90), 40),
            new HumancodeProperties.Problems("bank", 0, Duration.ofSeconds(180), ""));

    private final TriggerEngine engine = new TriggerEngine(PROPS);

    private static final Problem PROBLEM = new Problem(
            "todo-list", "Todo List", "easy", List.of("dom"), "Build a small todo list.",
            List.of(new Problem.ProblemFile(
                    "app.js", "javascript", "// your code here", "function addTodo() {}")),
            List.of("Adding an item appends it to the list"),
            List.of("Actually, make the button yellow instead of green."),
            List.of("Notes App"));

    private SessionState session() {
        return new SessionState("s1", PROBLEM, "javascript");
    }

    /** One newline added: the candidate pressed enter and settled the buffer. */
    private LineActivity oneLine() {
        return new LineActivity(1, 0);
    }

    @Test
    @DisplayName("a quiet, freshly-started session produces no trigger")
    void quietSessionIsSilent() {
        assertTrue(engine.evaluate(session()).isEmpty(),
                "a brand new session should not immediately provoke the interviewer");
    }

    @Test
    @DisplayName("typing normally produces no trigger")
    void activeTypingIsSilent() {
        SessionState state = session();
        state.recordEdit(50, 2);
        assertTrue(engine.evaluate(state).isEmpty());
    }

    @Test
    @DisplayName("mid-line typing produces nothing at all, however much of it there is")
    void nothingFiresWhileTheCandidateIsMidLine() {
        SessionState state = session();
        state.recordEdit(400, 0);

        // 400 characters clears every character threshold in the class. None of
        // them may fire, because the candidate has not finished a line: this is
        // the whole point of the gate.
        assertTrue(engine.onMeaningfulEdit(state, 400, 0, LineActivity.NONE).isEmpty(),
                "reacting to an unfinished line is the behaviour the gate exists to stop");
    }

    @Test
    @DisplayName("the first finished line gets one immediate acknowledgement")
    void firstImplementationFiresOnce() {
        SessionState state = session();
        state.recordEdit(20, 0);

        Trigger first = engine.onMeaningfulEdit(state, 20, 0, oneLine()).orElseThrow();
        assertEquals(Trigger.Kind.FIRST_IMPLEMENTATION, first.kind());
        assertFalse(first.cooldown(), "the first implementation should feel immediate");

        // It does not repeat, but the next finished line is still a reaction
        // opportunity, so this one falls through to LINE_COMPLETED rather than
        // to silence.
        assertEquals(Trigger.Kind.LINE_COMPLETED,
                engine.onMeaningfulEdit(state, 20, 0, oneLine()).orElseThrow().kind(),
                "the first-edit acknowledgement must not repeat");
    }

    @Test
    @DisplayName("the first implementation waits for a line rather than for 12 characters")
    void firstImplementationWaitsForALine() {
        SessionState state = session();
        state.recordEdit(20, 0);

        assertTrue(engine.onMeaningfulEdit(state, 20, 0, LineActivity.NONE).isEmpty(),
                "this one is immediate, so firing it mid-word skips the cooldown too");

        assertEquals(Trigger.Kind.FIRST_IMPLEMENTATION,
                engine.onMeaningfulEdit(state, 20, 0, oneLine()).orElseThrow().kind(),
                "and it must still land on the line they do finish");
    }

    @Test
    @DisplayName("completed lines create a cooldown-governed reaction opportunity")
    void completedLineFiresWithCooldown() {
        SessionState state = session();
        state.recordEdit(4, 0);

        Trigger trigger = engine.onMeaningfulEdit(state, 4, 0, oneLine()).orElseThrow();
        assertEquals(Trigger.Kind.LINE_COMPLETED, trigger.kind());
        assertTrue(trigger.cooldown());
    }

    @Test
    @DisplayName("a large deletion is noticed without waiting for the heartbeat")
    void heavyDeleteFires() {
        SessionState state = session();
        state.recordEdit(0, 100);

        // Deleting lines settles the buffer too. Throwing work away is a
        // decision, not typing in progress.
        Trigger trigger = engine.onMeaningfulEdit(state, 0, 100, new LineActivity(0, 3)).orElseThrow();
        assertEquals(Trigger.Kind.HEAVY_DELETE, trigger.kind());
        assertTrue(trigger.cooldown());
    }

    @Test
    @DisplayName("deleting inside one line is still mid-line")
    void backspacingWithinALineIsSilent() {
        SessionState state = session();
        state.recordEdit(0, 100);

        assertTrue(engine.onMeaningfulEdit(state, 0, 100, LineActivity.NONE).isEmpty(),
                "holding backspace is not a decision to answer for");
    }

    @Test
    @DisplayName("a large paste fires immediately and bypasses the cooldown")
    void largePasteFires() {
        SessionState state = session();
        Optional<Trigger> trigger = engine.onPaste(state, 400);

        assertTrue(trigger.isPresent());
        assertEquals(Trigger.Kind.PASTE_BURST, trigger.get().kind());
        assertFalse(trigger.get().cooldown(), "a paste must be called out as it happens");
    }

    @Test
    @DisplayName("a small paste is ignored")
    void smallPasteIsIgnored() {
        assertTrue(engine.onPaste(session(), 20).isEmpty());
    }

    @Test
    @DisplayName("deleting far more than writing fires the thrash trigger exactly once")
    void thrashFiresOnce() {
        SessionState state = session();
        state.recordEdit(300, 600);

        Optional<Trigger> first = engine.evaluate(state);
        assertTrue(first.isPresent());
        assertEquals(Trigger.Kind.MASS_DELETION, first.get().kind());

        // Same state, second look: the one-shot guard must hold.
        Optional<Trigger> second = engine.evaluate(state);
        assertTrue(second.isEmpty() || second.get().kind() != Trigger.Kind.MASS_DELETION,
                "the thrash trigger must not repeat on every tick");
    }

    @Test
    @DisplayName("submitting fires a trigger and bumps the submit count")
    void submittingFires() {
        SessionState state = session();
        state.recordSubmit();

        Trigger trigger = engine.onSubmit(state).orElseThrow();
        assertEquals(Trigger.Kind.SUBMITTED, trigger.kind());
        assertTrue(trigger.cooldown(), "rapid submits should not create rapid model calls");
        assertEquals(1, state.submitCount());
    }

    @Test
    @DisplayName("a curveball never fires before its delay or minimum characters are met")
    void curveballWaitsForDelayAndProgress() {
        SessionState state = session();
        state.recordEdit(200, 0);
        // Elapsed time is effectively zero in a unit test, well under the 90s delay.
        assertTrue(engine.evaluate(state).isEmpty());
    }

    @Test
    @DisplayName("a problem with no curveballs never fires one")
    void noCurveballsMeansNoCurveballTrigger() {
        Problem noCurveballs = new Problem(
                "x", "X", "easy", List.of(), "Build something.",
                List.of(new Problem.ProblemFile("app.js", "javascript", "", "")),
                List.of("Does something"), List.of(), List.of());
        SessionState state = new SessionState("s2", noCurveballs, "javascript");
        state.recordEdit(200, 0);
        assertTrue(engine.evaluate(state).isEmpty());
    }

    @Test
    @DisplayName("a finished session never triggers")
    void finishedSessionIsSilent() {
        SessionState state = session();
        state.recordEdit(300, 600);
        state.phase(Phase.DONE);
        assertTrue(engine.evaluate(state).isEmpty());
    }

    @Test
    @DisplayName("impatience is clamped to 0-100")
    void impatienceIsClamped() {
        SessionState state = session();
        state.bumpImpatience(500);
        assertEquals(100, state.impatience());
        state.bumpImpatience(-500);
        assertEquals(0, state.impatience());
    }
}
