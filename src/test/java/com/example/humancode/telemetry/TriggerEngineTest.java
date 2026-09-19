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
import com.example.humancode.problem.TestCase;

/**
 * The trigger engine is the thing standing between the candidate's keyboard and
 * the API bill, so its rules are worth testing directly.
 */
class TriggerEngineTest {

    private static final HumancodeProperties PROPS = new HumancodeProperties(
            new HumancodeProperties.Ai("", "gpt-5", "gpt-5-mini", Duration.ofSeconds(30)),
            new HumancodeProperties.Interview(
                    Duration.ofSeconds(20), Duration.ofSeconds(15), Duration.ofMillis(1500), "senior-engineer"));

    private final TriggerEngine engine = new TriggerEngine(PROPS);

    private static final Problem PROBLEM = new Problem(
            "two-sum", "Two Sum", "easy", List.of("array"), "Find two indices.", List.of(),
            "function twoSum(nums, target) {}", "twoSum",
            List.of(new TestCase(List.of(List.of(2, 7), 9), List.of(0, 1))), "unordered",
            "function twoSum() {}", "O(n)", List.of(), List.of(), List.of());

    private SessionState session() {
        return new SessionState("s1", PROBLEM, "senior-engineer", "javascript");
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
    @DisplayName("passing tests pushes impatience down, not up")
    void passingTestsCalmsTheInterviewer() {
        SessionState state = session();
        state.recordRun(true);

        Trigger trigger = engine.onRun(state, true, "3/3 assertions passed.").orElseThrow();
        assertEquals(Trigger.Kind.TESTS_PASSED, trigger.kind());
        assertTrue(trigger.urgency() < 0, "a green run should lower the meter");
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
