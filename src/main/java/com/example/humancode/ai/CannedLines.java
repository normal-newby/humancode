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
     * two different interviewers. Every line is second person, 3 to 14 words,
     * one sentence ending, and free of the words ReactionGuard forbids.
     */
    private static final Map<Trigger.Kind, List<String>> LINES = Map.ofEntries(
            Map.entry(Trigger.Kind.NO_START, List.of(
                    "Why is the editor still empty?",
                    "What exactly are you waiting for?",
                    "Are you planning to type anything today?")),
            Map.entry(Trigger.Kind.IDLE, List.of(
                    "Why are you still not changing anything?",
                    "What are you staring at?",
                    "Should I come back later?")),
            Map.entry(Trigger.Kind.PASTE_BURST, List.of(
                    "Where did that block just come from?",
                    "Want to explain that paste to me?",
                    "Did you write any of that yourself?")),
            Map.entry(Trigger.Kind.FIRST_IMPLEMENTATION, List.of(
                    "Took you long enough to start.",
                    "Is this finally going somewhere?",
                    "What took you so long to begin?")),
            Map.entry(Trigger.Kind.LINE_COMPLETED, List.of(
                    "One line, seriously?",
                    "What is that line supposed to be doing?",
                    "Is that line earning its place?")),
            Map.entry(Trigger.Kind.SUBSTANTIAL_EDIT, List.of(
                    "Where was all this ten minutes ago?",
                    "Fine, but do you believe any of it?")),
            Map.entry(Trigger.Kind.HEAVY_DELETE, List.of(
                    "Why did you just throw that away?",
                    "Was any of that worth keeping?")),
            Map.entry(Trigger.Kind.MASS_DELETION, List.of(
                    "Why are you deleting more than you write?",
                    "How many rewrites is this now?",
                    "Do you actually have a plan here?")),
            Map.entry(Trigger.Kind.TESTS_FAILED, List.of(
                    "Why are the tests still red?",
                    "What did you think that would do?",
                    "Did you read the failure at all?")),
            Map.entry(Trigger.Kind.TESTS_PASSED, List.of(
                    "Green at last, what took you?",
                    "It works, but can you tell me why?",
                    "Happy with how long that took?")),
            Map.entry(Trigger.Kind.SLOW_PROGRESS, List.of(
                    "What have you actually done so far?",
                    "Why is this taking you so long?")));

    private CannedLines() {
    }

    static Reaction forTrigger(Trigger trigger, int impatience, List<Utterance> transcript) {
        List<String> options = LINES.getOrDefault(trigger.kind(), List.of("What are you doing exactly?"));
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
