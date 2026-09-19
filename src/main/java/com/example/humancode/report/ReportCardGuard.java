package com.example.humancode.report;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.humancode.ai.SolutionLanguage;

/**
 * Same spirit as {@link com.example.humancode.ai.ReactionGuard}, sized for the
 * report card: the verdict is allowed several sentences where a live
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
    /**
     * Four. The annoyed register spends two sentences before it says anything —
     * "What is this? My app does not work." — and the prompt then asks for what
     * they tried and what happened instead, which is another one or two. Three
     * rejected exactly that verdict, in exactly that voice, and handed back the
     * canned one. The word cap is what actually bounds the length.
     */
    private static final int MAX_VERDICT_SENTENCES = 4;
    /**
     * Zero, not one. This floor was the worst bug the guard ever had: a model
     * that correctly judged an app to be working returned no barbs, because the
     * WORKS register is restraint and there was nothing to be barbed about. The
     * guard rejected the whole report for it and the canned fallback took over —
     * and the canned fallback, which cannot prove an app works, announced that
     * the app did not. Good work was told it was broken, by a rule meant to keep
     * the tone sharp.
     *
     * <p>A report with no insults is not empty. The verdict is the report.
     */
    private static final int MIN_INSULTS = 0;
    private static final int MAX_INSULTS = 4;
    private static final int MAX_COMPLIMENTS = 2;

    public boolean isSafe(GeneratedReport report) {
        return reject(report).isEmpty();
    }

    /**
     * Why this report cannot be used, or empty when it can.
     *
     * <p>A boolean was not enough. A rejection here is not an error, it is the
     * canned report, so the only sign anything went wrong is a {@code canned}
     * marker on screen. "Rejected an unsafe generated report" then tells you that
     * a rule fired and not which one, and these rules are word counts that a
     * re-tuned prompt drifts past by one. Naming the rule and quoting the text is
     * the difference between a one-minute fix and an afternoon.
     */
    public Optional<String> reject(GeneratedReport report) {
        if (report == null) {
            return Optional.of("no report");
        }
        if (report.outcome() == null) {
            return Optional.of("no outcome");
        }
        Optional<String> verdict = rejectVerdict(report.verdict());
        if (verdict.isPresent()) {
            return verdict;
        }
        Optional<String> insults = rejectLines("insults", report.insults(), MIN_INSULTS, MAX_INSULTS);
        if (insults.isPresent()) {
            return insults;
        }
        return rejectLines("compliments", report.compliments(), 0, MAX_COMPLIMENTS);
    }

    private Optional<String> rejectVerdict(String verdict) {
        if (verdict == null || verdict.isBlank()) {
            return Optional.of("blank verdict");
        }
        if (verdict.length() > 400) {
            return Optional.of("verdict is %d characters, over 400: \"%s\""
                    .formatted(verdict.length(), verdict));
        }
        int words = verdict.trim().split("\\s+").length;
        if (words < MIN_VERDICT_WORDS || words > MAX_VERDICT_WORDS) {
            return Optional.of("verdict is %d words, outside %d-%d: \"%s\""
                    .formatted(words, MIN_VERDICT_WORDS, MAX_VERDICT_WORDS, verdict));
        }
        int sentences = sentenceEndings(verdict);
        if (sentences < 1 || sentences > MAX_VERDICT_SENTENCES) {
            return Optional.of("verdict is %d sentences, over %d: \"%s\""
                    .formatted(sentences, MAX_VERDICT_SENTENCES, verdict));
        }
        return rejectVoice("verdict", verdict);
    }

    private Optional<String> rejectLines(String field, List<String> lines, int min, int max) {
        if (lines == null) {
            return min == 0 ? Optional.empty() : Optional.of("%s are missing".formatted(field));
        }
        if (lines.size() < min || lines.size() > max) {
            return Optional.of("%d %s, outside %d-%d".formatted(lines.size(), field, min, max));
        }
        for (String line : lines) {
            Optional<String> rejected = rejectLine(field, line);
            if (rejected.isPresent()) {
                return rejected;
            }
        }
        return Optional.empty();
    }

    private Optional<String> rejectLine(String field, String line) {
        if (line == null || line.isBlank()) {
            return Optional.of("blank %s line".formatted(field));
        }
        if (line.length() > 120) {
            return Optional.of("%s line is %d characters, over 120: \"%s\""
                    .formatted(field, line.length(), line));
        }
        int words = line.trim().split("\\s+").length;
        if (words < 3 || words > MAX_LINE_WORDS) {
            return Optional.of("%s line is %d words, outside 3-%d: \"%s\""
                    .formatted(field, words, MAX_LINE_WORDS, line));
        }
        if (sentenceEndings(line) > 1) {
            return Optional.of("%s line is more than one sentence: \"%s\"".formatted(field, line));
        }
        return rejectVoice(field, line);
    }

    private Optional<String> rejectVoice(String field, String text) {
        if (forbiddenPunctuation(text)) {
            return Optional.of("%s uses punctuation the voice does not: \"%s\"".formatted(field, text));
        }
        if (SolutionLanguage.mentioned(text)) {
            return Optional.of("%s gives away the solution: \"%s\"".formatted(field, text));
        }
        return Optional.empty();
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
