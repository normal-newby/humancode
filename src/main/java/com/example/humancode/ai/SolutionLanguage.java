package com.example.humancode.ai;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared coaching-language detector. {@link ReactionGuard} uses it for live
 * reactions and the report card's own guard uses it for the closing verdict —
 * naming a strategy or sequencing steps toward one is coaching regardless of
 * which call produced the line.
 */
public final class SolutionLanguage {

    private static final List<Pattern> PATTERNS = List.of(
            // Named strategies only — naming one of these *is* the hint, so they
            // are blocked outright regardless of context.
            Pattern.compile("(?i)\\b(two pointers|binary search|sliding window|dynamic programming|"
                    + "divide and conquer|greedy algorithm|prefix sum|backtrack\\w*|memoiz\\w*)\\b"),
            // Sequencing language strung together with an action — "sort, then scan
            // to merge" is a recipe even though none of those nouns is banned alone.
            Pattern.compile("(?i)\\b(first|then|next|instead)\\b.*\\b(write|use|make|check|return|add|remove|"
                    + "sort|scan|merge|traverse|iterate|reverse|filter|group)\\b"),
            Pattern.compile("(?i)\\b(you (should|could|need to|want to)|try using|consider (a|using)|"
                    + "just use)\\b"));

    private SolutionLanguage() {
    }

    public static boolean mentioned(String text) {
        return text != null && PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}
