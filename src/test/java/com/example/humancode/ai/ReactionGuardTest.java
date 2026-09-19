package com.example.humancode.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReactionGuardTest {

    private final ReactionGuard guard = new ReactionGuard();

    @Test
    void acceptsShortPlainReactions() {
        assertTrue(guard.isSafe(reaction("The editor is still empty.")));
    }

    @Test
    void acceptsTheAccusatoryQuestionsTheVoiceIsBuiltOn() {
        assertTrue(guard.isSafe(reaction("Why are you still not changing anything?")));
        assertTrue(guard.isSafe(reaction("What the hell is that line doing in there?")));
        assertTrue(guard.isSafe(reaction("Where did that block come from?")));
    }

    /** Seventeen words. The cap is fourteen, and it is still a cap. */
    @Test
    void stillRejectsAParagraph() {
        assertFalse(guard.isSafe(reaction(
                "Why are you still sitting there not writing anything at all right now"
                        + " on this problem today?")));
    }

    @Test
    void rejectsSolutionSteps() {
        assertFalse(guard.isSafe(reaction("Sort by start, then scan to merge.")));
    }

    @Test
    void rejectsTheForbiddenPunctuationStyle() {
        assertFalse(guard.isSafe(reaction("Stop drafting — write code now.")));
    }

    @Test
    void rejectsSeveralStackedSentences() {
        assertFalse(guard.isSafe(reaction("Do it faster. Time is passing.")));
    }

    private Reaction reaction(String line) {
        return new Reaction(Reaction.Verdict.WRONG, line, Reaction.Mood.IMPATIENT, 5,
                "Watching the editor.");
    }
}
