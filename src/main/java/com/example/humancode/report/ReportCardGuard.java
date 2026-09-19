package com.example.humancode.report;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.humancode.ai.SolutionLanguage;

/**
 * Same spirit as {@link com.example.humancode.ai.ReactionGuard}, sized for the
 * report card: the verdict is allowed two to three sentences where a live
 * reaction gets exactly one, and there are several lines to check instead of
 * one.
 */
@Component
public class ReportCardGuard {

    private static final int MAX_LINE_WORDS = 12;
    private static final int MIN_VERDICT_WORDS = 8;
    private static final int MAX_VERDICT_WORDS = 60;
    private static final int MAX_INSULTS = 4;
    private static final int MAX_COMPLIMENTS = 2;

    public boolean isSafe(GeneratedReport report) {
        return report != null
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
        if (sentences < 1 || sentences > 3) {
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
