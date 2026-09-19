package com.example.humancode.report;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.humancode.ai.SolutionLanguage;

/**
 * Same spirit as {@link com.example.humancode.ai.ReactionGuard}, sized for the
 * report card: the verdict is allowed up to three sentences where a live
 * reaction gets exactly one, and there are several lines to check instead of
 * one.
 *
 * <p>It deliberately does not police the verdict's <em>tone</em> against
 * {@link GeneratedReport#outcome()}. A mechanical check for "does this sound
 * annoyed enough" would reject far more good lines than bad ones, and the cost
 * of a rejection here is the canned report, not a retry.
 */
@Component
public class ReportCardGuard {

    private static final int MAX_LINE_WORDS = 12;
    /**
     * Two, not eight. The whole point of the closing reaction is that a good one
     * is curt — "Hmm. Not bad." is three words and is exactly what WORKS is
     * supposed to sound like. A floor of eight rejected it, and a rejected
     * report is not an error, it is the canned one (CLAUDE.md §8), so the tone
     * would have quietly failed shut with nothing in the log but a `canned`
     * marker on screen.
     */
    private static final int MIN_VERDICT_WORDS = 2;
    private static final int MAX_VERDICT_WORDS = 60;
    /** Short sentences are the register, so three of them fit inside the cap. */
    private static final int MAX_VERDICT_SENTENCES = 3;
    private static final int MAX_INSULTS = 4;
    private static final int MAX_COMPLIMENTS = 2;

    public boolean isSafe(GeneratedReport report) {
        return report != null
                && report.outcome() != null
                && safeVerdict(report.verdict())
                && safeLines(report.insults(), 1, MAX_INSULTS)
                && safeLines(report.compliments(), 0, MAX_COMPLIMENTS);
    }

    private boolean safeVerdict(String verdict) {
        if (verdict == null || verdict.isBlank() || verdict.length() > 400) {
            return false;
        }
        int words = verdict.trim().split("\\s+").length;
        if (words < MIN_VERDICT_WORDS || words > MAX_VERDICT_WORDS) {
            return false;
        }
        int sentences = sentenceEndings(verdict);
        if (sentences < 1 || sentences > MAX_VERDICT_SENTENCES) {
            return false;
        }
        return !forbiddenPunctuation(verdict) && !SolutionLanguage.mentioned(verdict);
    }

    private boolean safeLines(List<String> lines, int min, int max) {
        if (lines == null) {
            return min == 0;
        }
        if (lines.size() < min || lines.size() > max) {
            return false;
        }
        return lines.stream().allMatch(this::safeLine);
    }

    private boolean safeLine(String line) {
        if (line == null || line.isBlank() || line.length() > 120) {
            return false;
        }
        int words = line.trim().split("\\s+").length;
        if (words < 3 || words > MAX_LINE_WORDS || sentenceEndings(line) > 1) {
            return false;
        }
        return !forbiddenPunctuation(line) && !SolutionLanguage.mentioned(line);
    }

    private boolean forbiddenPunctuation(String text) {
        return text.contains("—") || text.contains("–") || text.contains(";") || text.contains(":")
                || text.contains("...") || text.contains("…") || text.contains("\n") || text.contains("#")
                || text.contains("`") || text.startsWith("- ") || text.startsWith("* ");
    }

    private int sentenceEndings(String text) {
        return (int) text.chars().filter(c -> c == '.' || c == '!' || c == '?').count();
    }
}
