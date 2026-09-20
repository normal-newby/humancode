package com.example.humancode.speech;

import com.example.humancode.ai.Reaction.Mood;

/**
 * How a line is said, as opposed to what it says.
 *
 * <p><b>One voice, five deliveries.</b> The voice id never changes with mood,
 * for exactly the reason UI-DESIGN.md §6a gives for the face keeping the same
 * head in all five expressions: two lines a beat apart have to read as one
 * person changing their mind, not as two people. Swapping the voice id per mood
 * would undo that in the most obvious way available.
 *
 * <p>What changes instead is the delivery, and the lever is an <b>audio tag</b>
 * — a bracketed instruction {@code eleven_v3} interprets as performance rather
 * than reading aloud. That was measured, not assumed: synthesising the bare
 * text {@code [shouting]} on its own returns 284 bytes of silence, while the
 * word {@code shouting} returns 1.1 seconds of speech.
 *
 * <p><b>Tags are a v3 feature and {@link SpeechService} checks the model before
 * applying them.</b> On {@code eleven_turbo_v2_5} or {@code eleven_flash_v2_5}
 * the same string is read out as the word "shouting", which is the single most
 * embarrassing failure this file could ship.
 *
 * <p>{@code speed} is deliberately absent. Turbo honours it (1.15 and 0.85
 * produce audibly different lengths); v3 ignores it outright — the same request
 * at 1.05 and 1.15 came back byte-identical. Sending it anyway would read as
 * pacing being handled when it is not. Pace comes from the tag.
 */
public enum Delivery {

    /** They are impressed, which for this interviewer means quietly. */
    CALM(null, 0.5, 0.30),

    /** Dry, enjoying itself at your expense. */
    WRY("[amused]", 0.5, 0.50),

    /** The default. Stating a fact you will not enjoy. */
    FLAT(null, 0.5, 0.15),

    /** Patience visibly going. Clipped and pushed. */
    TENSE("[annoyed]", 0.0, 0.70),

    /** Out of patience. This is the one that yells. */
    YELLING("[shouting]", 0.0, 1.00);

    /** At or above this the meter starts tightening a line the mood left loose. */
    static final int TENSE_ABOVE = 60;
    /** At or above this an already-impatient line becomes a shouted one. */
    static final int YELL_ABOVE = 85;

    private final String tag;
    /**
     * v3 reads stability as three settings, not a slider: 0.0 creative,
     * 0.5 natural, 1.0 robust. Lower is more expressive and more willing to
     * follow a tag somewhere extreme, which is what the angry end needs.
     */
    private final double stability;
    private final double style;

    Delivery(String tag, double stability, double style) {
        this.tag = tag;
        this.stability = stability;
        this.style = style;
    }

    public double stability() {
        return stability;
    }

    public double style() {
        return style;
    }

    /** The line with its performance direction in front of it, or unchanged. */
    public String apply(String line) {
        return tag == null ? line : tag + " " + line;
    }

    /** For logging: what this delivery actually asked for. */
    public String tag() {
        return tag == null ? "none" : tag;
    }

    /**
     * Mood decides the register; the meter can escalate it.
     *
     * <p>Mood is the interviewer's read on <em>this line</em> and impatience is
     * how much patience they have left overall, so both belong here — they are
     * the same two signals the two faces in §6a are drawn from. Mood sets the
     * base and a high meter pushes it one step:
     *
     * <p><b>{@link #CALM} never escalates.</b> A rare IMPRESSED line at
     * impatience 90 is grudging approval from someone furious, and delivering
     * it flat is funnier and more in character than delivering it shouted —
     * "being unimpressed by working code is in character" (CLAUDE.md §5) cuts
     * both ways. It is also the only thing keeping the top of the meter from
     * being one continuous shout.
     */
    public static Delivery forLine(Mood mood, int impatience) {
        Delivery base = switch (mood == null ? Mood.NEUTRAL : mood) {
            case IMPRESSED -> CALM;
            case AMUSED -> WRY;
            case NEUTRAL -> FLAT;
            case IMPATIENT -> TENSE;
            case EXASPERATED -> YELLING;
        };
        if (base == TENSE && impatience >= YELL_ABOVE) {
            return YELLING;
        }
        if ((base == FLAT || base == WRY) && impatience >= TENSE_ABOVE) {
            return TENSE;
        }
        return base;
    }

    /**
     * For the closing verdict, which has no mood of its own.
     *
     * <p>It is read off the meter for the same reason the report card's face is
     * (UI-DESIGN.md §6a): {@code GeneratedReport.outcome} knows whether the app
     * works and deliberately never leaves the server, so a delivery driven by
     * it would be the pass/fail badge §4.7 forbids — announced out loud, which
     * is worse than drawn.
     */
    public static Delivery forImpatience(int impatience) {
        if (impatience >= YELL_ABOVE) {
            return YELLING;
        }
        if (impatience >= TENSE_ABOVE) {
            return TENSE;
        }
        return FLAT;
    }
}
