package com.example.humancode.ai;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Rejects completions that would turn a reaction into coaching. */
@Component
public class ReactionGuard {

    private static final List<Pattern> SOLUTION_LANGUAGE = List.of(
            Pattern.compile("(?i)\\b(sort|merge|scan|traverse|iterate|loop|hash ?map|stack|queue|"
                    + "set|two pointers|binary search|sliding window|dynamic programming|recursion|"
                    + "greedy|prefix sum|backtrack|memoiz)\\b"),
            Pattern.compile("(?i)\\b(first|then|next)\\b.*\\b(write|use|make|check|return|add|remove)\\b"));

    public boolean isSafe(Reaction reaction) {
        return reaction != null && safeLine(reaction.line()) && hasNoSolutionLanguage(reaction.note());
    }

    private boolean safeLine(String line) {
        if (line == null || line.isBlank() || line.length() > 120) {
            return false;
        }
        int words = line.trim().split("\\s+").length;
        if (words < 3 || words > 12) {
            return false;
        }
        if (line.contains("—") || line.contains("–") || line.contains(";") || line.contains(":")
                || line.contains("...") || line.contains("…") || line.contains("\n") || line.contains("#")
                || line.contains("`") || line.startsWith("- ") || line.startsWith("* ")
                || sentenceEndings(line) > 1) {
            return false;
        }
        return hasNoSolutionLanguage(line);
    }

    private boolean hasNoSolutionLanguage(String text) {
        return text != null && SOLUTION_LANGUAGE.stream().noneMatch(pattern -> pattern.matcher(text).find());
    }

    private int sentenceEndings(String text) {
        return (int) text.chars().filter(c -> c == '.' || c == '!' || c == '?').count();
    }
}
