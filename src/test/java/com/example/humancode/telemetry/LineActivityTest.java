package com.example.humancode.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LineActivity} is the gate that decides whether the interviewer is
 * allowed to speak at all on an edit, so getting it wrong either makes the app
 * silent or puts it back to heckling half-typed words.
 */
class LineActivityTest {

    @Test
    @DisplayName("typing within a line is not settled")
    void typingWithinALineIsNotSettled() {
        LineActivity lines = LineActivity.between(
                Map.of("app.js", "const total = arr"),
                Map.of("app.js", "const total = arr.filt"));

        assertEquals(0, lines.completed());
        assertEquals(0, lines.removed());
        assertFalse(lines.settled());
    }

    @Test
    @DisplayName("pressing enter settles the buffer")
    void newlineSettles() {
        LineActivity lines = LineActivity.between(
                Map.of("app.js", "const total = 0"),
                Map.of("app.js", "const total = 0\n"));

        assertEquals(1, lines.completed());
        assertTrue(lines.settled());
    }

    @Test
    @DisplayName("deleting lines settles the buffer too")
    void removingLinesSettles() {
        LineActivity lines = LineActivity.between(
                Map.of("app.js", "one\ntwo\nthree\n"),
                Map.of("app.js", "one\n"));

        assertEquals(0, lines.completed());
        assertEquals(2, lines.removed());
        assertTrue(lines.settled());
    }

    @Test
    @DisplayName("a file the session has not seen contributes nothing")
    void unknownFileContributesNothing() {
        // Its starter newlines are not lines the candidate wrote, and counting
        // them would fire a reaction on the first batch of every session.
        LineActivity lines = LineActivity.between(
                Map.of(),
                Map.of("index.html", "<html>\n<body>\n</body>\n</html>\n"));

        assertEquals(LineActivity.NONE, lines);
        assertFalse(lines.settled());
    }

    @Test
    @DisplayName("line activity is summed across files")
    void activityIsSummedAcrossFiles() {
        LineActivity lines = LineActivity.between(
                Map.of("app.js", "a\n", "style.css", "x\ny\n"),
                Map.of("app.js", "a\nb\n", "style.css", "x\n"));

        assertEquals(1, lines.completed());
        assertEquals(1, lines.removed());
        assertTrue(lines.settled());
    }

    @Test
    void aNullFileMapIsNotSettled() {
        assertEquals(LineActivity.NONE, LineActivity.between(Map.of("app.js", "a"), null));
    }
}
