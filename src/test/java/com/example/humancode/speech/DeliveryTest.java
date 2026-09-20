package com.example.humancode.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.humancode.ai.Reaction.Mood;

/**
 * The one thing about the voice that is a decision rather than a setting: how
 * angry the interviewer sounds, and when it starts shouting.
 *
 * <p>Worth pinning because it fails silently in the worst way — nothing in a
 * log says "that line should have been yelled", and nobody notices a delivery
 * that quietly stopped escalating until a demo is flat.
 */
class DeliveryTest {

    @Test
    void moodPicksTheRegister() {
        assertSame(Delivery.CALM, Delivery.forLine(Mood.IMPRESSED, 0));
        assertSame(Delivery.WRY, Delivery.forLine(Mood.AMUSED, 0));
        assertSame(Delivery.FLAT, Delivery.forLine(Mood.NEUTRAL, 0));
        assertSame(Delivery.TENSE, Delivery.forLine(Mood.IMPATIENT, 0));
        assertSame(Delivery.YELLING, Delivery.forLine(Mood.EXASPERATED, 0));
    }

    @Test
    void exasperatedYellsAtAnyPointOnTheMeter() {
        // The mood *is* the anger. A calm meter does not make it a polite line.
        assertSame(Delivery.YELLING, Delivery.forLine(Mood.EXASPERATED, 0));
        assertSame(Delivery.YELLING, Delivery.forLine(Mood.EXASPERATED, 100));
    }

    @Test
    void aHighMeterTightensALineTheMoodLeftLoose() {
        assertSame(Delivery.FLAT, Delivery.forLine(Mood.NEUTRAL, Delivery.TENSE_ABOVE - 1));
        assertSame(Delivery.TENSE, Delivery.forLine(Mood.NEUTRAL, Delivery.TENSE_ABOVE));
        assertSame(Delivery.TENSE, Delivery.forLine(Mood.AMUSED, Delivery.TENSE_ABOVE));
    }

    @Test
    void anImpatientLineAtTheTopOfTheMeterBecomesAShout() {
        assertSame(Delivery.TENSE, Delivery.forLine(Mood.IMPATIENT, Delivery.YELL_ABOVE - 1));
        assertSame(Delivery.YELLING, Delivery.forLine(Mood.IMPATIENT, Delivery.YELL_ABOVE));
    }

    @Test
    void approvalIsNeverShouted() {
        // See Delivery's javadoc: grudging approval from someone furious is
        // funnier flat, and it is the only thing keeping the top of the meter
        // from being one continuous shout.
        assertSame(Delivery.CALM, Delivery.forLine(Mood.IMPRESSED, 100));
    }

    @Test
    void aNullMoodIsFlatRatherThanAnException() {
        // Mood arrives from model output. It has been null before.
        assertSame(Delivery.FLAT, Delivery.forLine(null, 0));
    }

    @Test
    void theVerdictReadsTheMeterBecauseItHasNoMood() {
        assertSame(Delivery.FLAT, Delivery.forImpatience(0));
        assertSame(Delivery.TENSE, Delivery.forImpatience(Delivery.TENSE_ABOVE));
        assertSame(Delivery.YELLING, Delivery.forImpatience(Delivery.YELL_ABOVE));
    }

    @Test
    void onlyTheAngryDeliveriesCarryATag() {
        assertEquals("why is that still there?", Delivery.FLAT.apply("why is that still there?"));
        assertEquals("why is that still there?", Delivery.CALM.apply("why is that still there?"));
        assertEquals("[shouting] why is that still there?",
                Delivery.YELLING.apply("why is that still there?"));
        assertEquals("[annoyed] why is that still there?",
                Delivery.TENSE.apply("why is that still there?"));
    }

    @Test
    void theAngryEndIsTheExpressiveEnd() {
        // v3 reads stability as creative/natural/robust rather than a slider,
        // and the shouted line needs the creative setting to follow the tag
        // somewhere extreme.
        assertEquals(0.0, Delivery.YELLING.stability());
        assertEquals(0.5, Delivery.FLAT.stability());
        assertTrue(Delivery.YELLING.style() > Delivery.FLAT.style());
        assertFalse(Delivery.FLAT.apply("x").startsWith("["));
    }
}
