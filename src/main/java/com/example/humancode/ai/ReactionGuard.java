package com.example.humancode.ai;

import org.springframework.stereotype.Component;

/** Rejects completions that would turn a reaction into coaching. */
@Component
public class ReactionGuard {

    private static final int MAX_WORDS = 14;

    public boolean isSafe(Reaction reaction) {
        return reaction != null && safeLine(reaction.line()) && !SolutionLanguage.mentioned(reaction.note());
    }

    private boolean safeLine(String line) {
        if (line == null || line.isBlank() || line.length() > 120) {
            return false;
        }
        int words = line.trim().split("\\s+").length;
        // 14, not 12: an accusatory question carries more scaffolding than
        // a flat statement, and "what the hell are you doing putting useless
        // lines in the code" is exactly twelve words. A rejected line is a
        // silent downgrade to a canned one, so leave the tone some room.
        if (words < 3 || words > MAX_WORDS) {
            return false;
        }
        if (line.contains("—") || line.contains("–") || line.contains(";") || line.contains(":")
                || line.contains("...") || line.contains("…") || line.contains("\n") || line.contains("#")
                || line.contains("`") || line.startsWith("- ") || line.startsWith("* ")
                || sentenceEndings(line) > 1) {
            return false;
        }
        return !SolutionLanguage.mentioned(line);
    }

    private int sentenceEndings(String text) {
        return (int) text.chars().filter(c -> c == '.' || c == '!' || c == '?').count();
    }
}
