package com.example.humancode.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.example.humancode.interview.Utterance;
import com.example.humancode.telemetry.Trigger;

/**
 * Fallback lines used when no OPENAI_API_KEY is configured, or when a model call
 * fails mid-session.
 *
 * <p>This is not just a stub: an interview that goes silent because a network
 * call timed out is a broken demo. The trigger engine, meter, SSE stream and UI
 * all exercise the same path whether the line came from the model or from here.
 */
final class CannedLines {

    /**
     * Same voice as the model, or the fallback gives the game away: an
     * accusation from the model followed by "One line done." from here reads as
     * two different interviewers. Every line is second person or dry
     * third-observation, 3 to 14 words, one sentence ending, and free of the
     * words ReactionGuard forbids. None of these lean on "why did you [verb]" —
     * that shape is a complaint with a question mark on it, not a joke, and the
     * fallback should not be blander than the model it is standing in for.
     */
    private static final Map<Trigger.Kind, List<String>> LINES = Map.ofEntries(
            Map.entry(Trigger.Kind.NO_START, List.of(
                    "The cursor has been blinking longer than you have been thinking.",
                    "An empty editor is a bold opening statement.",
                    "Somewhere, a cursor is aging.")),
            Map.entry(Trigger.Kind.IDLE, List.of(
                    "The cursor is doing more work than you are.",
                    "Silence, bold choice for a technical interview.",
                    "You have entered a staring contest with the code.")),
            Map.entry(Trigger.Kind.PASTE_BURST, List.of(
                    "That is a lot of clipboard for one candidate.",
                    "Ctrl+V, bold opening move.",
                    "That paste alone could sink this interview.")),
            Map.entry(Trigger.Kind.FIRST_IMPLEMENTATION, List.of(
                    "And we are finally airborne.",
                    "Look who decided to show up.",
                    "Ten minutes for four characters, remarkable pace.")),
            Map.entry(Trigger.Kind.LINE_COMPLETED, List.of(
                    "One line, and already a headline.",
                    "That line had better be worth the wait.",
                    "A single line, delivered like breaking news.")),
            Map.entry(Trigger.Kind.SUBSTANTIAL_EDIT, List.of(
                    "Where was all this ten minutes ago.",
                    "Suddenly productive, suspicious timing.",
                    "That is a lot of code, arriving very late.")),
            Map.entry(Trigger.Kind.HEAVY_DELETE, List.of(
                    "That code did not even get a eulogy.",
                    "Gone, just like the last ten minutes.",
                    "Deleted faster than it was written.")),
            Map.entry(Trigger.Kind.MASS_DELETION, List.of(
                    "This is less coding, more demolition.",
                    "Rewrite number four, and counting.",
                    "You are deleting a novel at this point.")),
            Map.entry(Trigger.Kind.SUBMITTED, List.of(
                    "And that is what you are handing me.",
                    "Submitted, bold statement of confidence.",
                    "We will see if that holds up.")),
            Map.entry(Trigger.Kind.SLOW_PROGRESS, List.of(
                    "Five minutes for this, quite the investment.",
                    "The clock is winning right now.",
                    "This pace could use a faster pace.")));

    private CannedLines() {
    }

    static Reaction forTrigger(Trigger trigger, int impatience, List<Utterance> transcript) {
        List<String> options = LINES.getOrDefault(trigger.kind(), List.of("That is one way to spend the time."));
        String previous = transcript.isEmpty() ? null : transcript.getLast().line();
        List<String> fresh = options.stream()
                .filter(line -> !line.equalsIgnoreCase(previous))
                .toList();
        List<String> candidates = fresh.isEmpty() ? options : fresh;
        String line = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        // NEUTRAL, always: there is no model here, so there is nothing that
        // read the code. The fallback moves the meter the way it always did,
        // on the trigger's own urgency, and the face follows the impatience it
        // already has rather than claiming a judgement it did not make.
        return new Reaction(Reaction.Verdict.NEUTRAL, line, moodFor(impatience),
                trigger.urgency(), noteFor(trigger));
    }

    private static Reaction.Mood moodFor(int impatience) {
        if (impatience >= 75) {
            return Reaction.Mood.EXASPERATED;
        }
        if (impatience >= 45) {
            return Reaction.Mood.IMPATIENT;
        }
        return Reaction.Mood.NEUTRAL;
    }

    private static String noteFor(Trigger trigger) {
        return switch (trigger.kind()) {
            case NO_START -> "Has not started. Staring.";
            case IDLE -> "Idle again.";
            case PASTE_BURST -> "Pasted a large block. Noted.";
            case FIRST_IMPLEMENTATION -> "Started an implementation.";
            case LINE_COMPLETED -> "Completed another line of code.";
            case SUBSTANTIAL_EDIT -> "Added a substantial chunk of code.";
            case HEAVY_DELETE -> "Deleted a large block of code.";
            case MASS_DELETION -> "Rewriting rather than progressing.";
            case SUBMITTED -> "Submitted for judgment.";
            // Unreachable in practice — Interviewer.react() short-circuits CURVEBALL
            // before CannedLines is ever consulted, since it never calls the model.
            case CURVEBALL -> "Sprung a curveball.";
            case SLOW_PROGRESS -> "Little progress for the time spent.";
        };
    }
}
