package com.example.humancode.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The handle rules, which are the whole account model — see {@link User}. The
 * normalization half matters most: two spellings of one name would be two rows
 * on the same leaderboard, which is the sort of thing nobody notices until the
 * board is on a projector.
 */
class HandlesTest {

    @Test
    void normalizesCaseAndSurroundingSpace() {
        assertEquals("nimo", Handles.require("  Nimo  "));
        assertEquals("nimo", Handles.require("NIMO"));
    }

    @Test
    void acceptsTheCharactersAShellWouldNotArgueWith() {
        assertEquals("a_b-c9", Handles.require("A_b-C9"));
    }

    @Test
    void rejectsTooShortAndTooLong() {
        assertThrows(Handles.InvalidHandleException.class, () -> Handles.require("a"));
        assertThrows(Handles.InvalidHandleException.class, () -> Handles.require("a".repeat(17)));
    }

    @Test
    void rejectsSpacesAndPunctuation() {
        assertThrows(Handles.InvalidHandleException.class, () -> Handles.require("two words"));
        assertThrows(Handles.InvalidHandleException.class, () -> Handles.require("drop;table"));
        assertThrows(Handles.InvalidHandleException.class, () -> Handles.require("emoji✨"));
    }

    @Test
    void rejectsTheNamesTheAppIsAlreadyUsing() {
        // `you` in particular: the tab, the cwd and the prelude all say it
        // about the candidate generally, and a specific candidate holding it
        // would make every one of those read wrong.
        assertThrows(Handles.InvalidHandleException.class, () -> Handles.require("you"));
        assertThrows(Handles.InvalidHandleException.class, () -> Handles.require("GPDETOX"));
    }

    @Test
    void normalizeToleratesNull() {
        assertEquals("", Handles.normalize(null));
    }
}
