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

    private static final Map<Trigger.Kind, List<String>> LINES = Map.ofEntries(
            Map.entry(Trigger.Kind.NO_START, List.of(
                    "The editor is still empty.",
                    "Time is passing.",
                    "Start working now.")),
            Map.entry(Trigger.Kind.IDLE, List.of(
                    "Still staring at it?",
                    "The cursor is bored.",
                    "You stopped moving.")),
            Map.entry(Trigger.Kind.PASTE_BURST, List.of(
                    "That paste was loud.",
                    "I saw the paste.",
                    "Very fast typing.")),
            Map.entry(Trigger.Kind.FIRST_IMPLEMENTATION, List.of(
                    "Actual code finally.",
                    "Something is happening.",
                    "There we go.")),
            Map.entry(Trigger.Kind.LINE_COMPLETED, List.of(
                    "One line done.",
                    "Keep it moving.",
                    "That line exists now.")),
            Map.entry(Trigger.Kind.SUBSTANTIAL_EDIT, List.of(
                    "That is more like it.",
                    "You finally moved.")),
            Map.entry(Trigger.Kind.HEAVY_DELETE, List.of(
                    "That was a large delete.",
                    "New plan now?")),
            Map.entry(Trigger.Kind.MASS_DELETION, List.of(
                    "You deleted more than you wrote.",
                    "Another rewrite now?",
                    "That was a lot of deleting.")),
            Map.entry(Trigger.Kind.TESTS_FAILED, List.of(
                    "Tests are still red.",
                    "Tests say no.",
                    "The tests disagree with you.")),
            Map.entry(Trigger.Kind.TESTS_PASSED, List.of(
                    "Green. I'll allow it.",
                    "It finally passes.",
                    "Fine that works.")),
            Map.entry(Trigger.Kind.SLOW_PROGRESS, List.of(
                    "Five minutes already.",
                    "Not much to show.")));

    private CannedLines() {
    }

    static Reaction forTrigger(Trigger trigger, int impatience, List<Utterance> transcript) {
        List<String> options = LINES.getOrDefault(trigger.kind(), List.of("Carry on."));
        String previous = transcript.isEmpty() ? null : transcript.getLast().line();
        List<String> fresh = options.stream()
                .filter(line -> !line.equalsIgnoreCase(previous))
                .toList();
        List<String> candidates = fresh.isEmpty() ? options : fresh;
        String line = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        return new Reaction(line, moodFor(impatience), trigger.urgency(), noteFor(trigger));
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
            case TESTS_FAILED -> "Tests failing.";
            case TESTS_PASSED -> "Tests green.";
            case SLOW_PROGRESS -> "Little progress for the time spent.";
        };
    }
}
