package com.example.humancode.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The meter has to answer to the verdict on the code, because that is the whole
 * claim the face in the log makes: a furious human means you broke something,
 * not that you were slow.
 */
class ReactionTest {

    @Test
    @DisplayName("a good verdict always earns patience back, whatever number came with it")
    void goodWorkCannotCost() {
        assertEquals(-6, reaction(Reaction.Verdict.GOOD, -6).alignedDelta());
        // The model contradicting itself is the case this exists for.
        assertEquals(-1, reaction(Reaction.Verdict.GOOD, 8).alignedDelta());
        assertEquals(-1, reaction(Reaction.Verdict.GOOD, 0).alignedDelta());
    }

    @Test
    @DisplayName("a wrong verdict always costs")
    void wrongWorkAlwaysCosts() {
        assertEquals(12, reaction(Reaction.Verdict.WRONG, 12).alignedDelta());
        assertEquals(1, reaction(Reaction.Verdict.WRONG, -5).alignedDelta());
    }

    @Test
    @DisplayName("neutral passes through untouched, in both directions")
    void neutralIsTheModelsCall() {
        // Nothing changed in the editor, so nothing here should be invented:
        // the trigger's own urgency is what moves the meter, as it always did.
        assertEquals(3, reaction(Reaction.Verdict.NEUTRAL, 3).alignedDelta());
        assertEquals(-2, reaction(Reaction.Verdict.NEUTRAL, -2).alignedDelta());
        assertEquals(0, reaction(Reaction.Verdict.NEUTRAL, 0).alignedDelta());
    }

    @Test
    @DisplayName("a missing verdict is read as neutral rather than throwing mid-session")
    void noVerdictIsNeutral() {
        // Structured output makes this near-impossible, but react() must never
        // throw: a session that goes silent is a broken demo (CLAUDE.md §5.1).
        assertEquals(4, new Reaction(null, "line", Reaction.Mood.NEUTRAL, 4, "note").alignedDelta());
    }

    private static Reaction reaction(Reaction.Verdict verdict, int delta) {
        return new Reaction(verdict, "That line is going nowhere.", Reaction.Mood.IMPATIENT, delta,
                "Watching the editor.");
    }
}
