package com.example.humancode.ai;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Keeps a hint a nudge rather than the answer.
 *
 * <p>Deliberately looser than {@link ReactionGuard}: the candidate asked for
 * this one, so "check whether your click handler is actually attached" is the
 * feature working, not coaching leaking into a quip — {@link SolutionLanguage}
 * blocks that exact phrasing, which is why this guard does not reuse it. What
 * still is not allowed through is literal code and the name of the specific
 * technique that would turn a nudge into the answer with a question mark
 * removed.
 */
@Component
public class HintGuard {

    private static final int MIN_WORDS = 4;
    private static final int MAX_WORDS = 45;
    private static final int MAX_LENGTH = 400;

    private static final Pattern NAMED_TECHNIQUE = Pattern.compile(
            "(?i)\\b(two pointers|binary search|sliding window|dynamic programming|"
                    + "divide and conquer|greedy algorithm|prefix sum|backtrack\\w*|memoiz\\w*)\\b");

    public boolean isSafe(Hint hint) {
        return hint != null && safeText(hint.text());
    }

    private boolean safeText(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_LENGTH) {
            return false;
        }
        if (text.contains("```") || text.contains("`")) {
            return false;
        }
        int words = text.trim().split("\\s+").length;
        if (words < MIN_WORDS || words > MAX_WORDS) {
            return false;
        }
        return !NAMED_TECHNIQUE.matcher(text).find();
    }
}
