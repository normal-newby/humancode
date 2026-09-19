package com.example.humancode.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.humancode.problem.Problem;

/**
 * The settled view is what stands between the interviewer and a half-typed
 * line, so it is worth pinning directly — the failure it prevents is invisible
 * in every other test, because it only shows up as a model saying something
 * about {@code const subtot}.
 */
class SessionStateSettledCodeTest {

    private static final Problem PROBLEM = new Problem(
            "cart", "Cart", "easy", List.of("dom"), "Build a cart.",
            List.of(new Problem.ProblemFile("app.js", "javascript", "", "done"),
                    new Problem.ProblemFile("styles.css", "css", "", "done")),
            List.of("It works"), List.of("Make it yellow."), List.of());

    private SessionState typing() {
        SessionState state = new SessionState("s1", PROBLEM, "javascript");
        // recordEdit stamps the last keystroke, which is what puts the session
        // inside the still-typing window.
        state.recordEdit(10, 0);
        return state;
    }

    @Test
    @DisplayName("a line still being typed is held back")
    void holdsBackTheLineUnderTheirFingers() {
        SessionState state = typing();
        state.code("app.js", "const total = 0;\nconst subtot");

        assertEquals("const total = 0;\n", state.settledCode().get("app.js"));
        assertEquals(List.of("app.js"), state.filesMidLine());
    }

    @Test
    @DisplayName("a buffer ending on a newline is already settled")
    void aFinishedBufferIsUntouched() {
        SessionState state = typing();
        state.code("app.js", "const total = 0;\n");

        assertEquals("const total = 0;\n", state.settledCode().get("app.js"));
        assertTrue(state.filesMidLine().isEmpty());
    }

    @Test
    @DisplayName("a single unfinished line is shown rather than hiding the whole file")
    void neverHidesEverything() {
        SessionState state = typing();
        state.code("app.js", "const subtot");

        // Holding this back would show an empty file and provoke "you have
        // written nothing", which is the same unfairness in reverse.
        assertEquals("const subtot", state.settledCode().get("app.js"));
        assertTrue(state.filesMidLine().isEmpty());
    }

    @Test
    @DisplayName("a candidate who has never typed is not protected by the typing window")
    void neverHavingTypedIsNotTyping() {
        SessionState state = new SessionState("s2", PROBLEM, "javascript");
        state.code("app.js", "const total = 0;\nconst subtot");

        // No keystroke has ever been recorded. idleFor() falls back to the start
        // of the session, so without an explicit check this would read as
        // furiously active typing for the first four seconds of every session.
        assertEquals("const total = 0;\nconst subtot", state.settledCode().get("app.js"));
        assertTrue(state.filesMidLine().isEmpty(),
                "an abandoned half-line needs no protection; it is the interviewer's business");
    }

    @Test
    @DisplayName("the diff baseline records what was shown, not what was typed")
    void baselineMatchesWhatWasShown() {
        SessionState state = typing();
        state.code("app.js", "const total = 0;\nconst subtot");

        state.markCodeSpokenFor();

        // Baseline on the raw buffer instead and the hidden fragment reappears
        // in the next diff as a deletion, so it gets reacted to one line late.
        assertEquals("const total = 0;\n", state.previousCode().get("app.js"));
    }

    @Test
    @DisplayName("each file settles on its own")
    void filesSettleIndependently() {
        SessionState state = typing();
        state.code("app.js", "a();\nb(");
        state.code("styles.css", "body {}\n");

        assertEquals("a();\n", state.settledCode().get("app.js"));
        assertEquals("body {}\n", state.settledCode().get("styles.css"));
        assertEquals(List.of("app.js"), state.filesMidLine());
    }
}
