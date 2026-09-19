package com.example.humancode.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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

    private static final Map<Trigger.Kind, List<String>> LINES = Map.of(
            Trigger.Kind.NO_START, List.of(
                    "The problem's been up for a while. The editor is still empty. Just noting that.",
                    "Take your time. I've only got the rest of the afternoon.",
                    "Blank file. Bold opening move."),
            Trigger.Kind.IDLE, List.of(
                    "Still with me?",
                    "That cursor hasn't moved in a while. Thinking, or stuck?",
                    "I can hear the clock from here.",
                    "Silence is a strategy, I suppose."),
            Trigger.Kind.PASTE_BURST, List.of(
                    "That appeared very quickly for something you typed.",
                    "Interesting. You paste faster than you type.",
                    "I saw that."),
            Trigger.Kind.MASS_DELETION, List.of(
                    "Third rewrite. Is the plan coming together or going away?",
                    "You've deleted more than you've written. Bold.",
                    "Ctrl+A is not an algorithm."),
            Trigger.Kind.TESTS_FAILED, List.of(
                    "Red. Again.",
                    "Not quite. Read the failing case out loud, it usually helps.",
                    "The tests disagree with you."),
            Trigger.Kind.TESTS_PASSED, List.of(
                    "Green. I'll allow it.",
                    "It passes. Now tell me the complexity.",
                    "Fine. That works. Don't look so pleased."),
            Trigger.Kind.SLOW_PROGRESS, List.of(
                    "Five minutes, and not much on the board.",
                    "We're a third of the way through the time and a tenth of the way through the problem."));

    private CannedLines() {
    }

    static Reaction forTrigger(Trigger trigger, int impatience) {
        List<String> options = LINES.getOrDefault(trigger.kind(), List.of("Carry on."));
        String line = options.get(ThreadLocalRandom.current().nextInt(options.size()));
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
            case MASS_DELETION -> "Rewriting rather than progressing.";
            case TESTS_FAILED -> "Tests failing.";
            case TESTS_PASSED -> "Tests green.";
            case SLOW_PROGRESS -> "Little progress for the time spent.";
        };
    }
}
