package com.example.humancode.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A line-level diff of the candidate's editor, for the prompt.
 *
 * <p>Handing the model only the current buffer makes it comment on the same
 * shape of code over and over, because from its point of view nothing ever
 * happened. Handing it what changed since it last spoke is what makes a
 * reaction specific: it can name the loop that just appeared, or the ten lines
 * that just vanished, and it cannot repeat itself without noticing.
 *
 * <p>Plain LCS over lines. The buffers here are interview answers, tens of
 * lines, so the quadratic table is free; {@link #MAX_INPUT_LINES} caps a pasted
 * novel before it can cost anything.
 */
public final class CodeDiff {

    /** Beyond this the diff is noise in the prompt and tokens on the bill. */
    private static final int MAX_OUTPUT_LINES = 40;
    private static final int MAX_INPUT_LINES = 400;
    private static final int MAX_LINE_CHARS = 120;

    private CodeDiff() {
    }

    /**
     * Renders the changes from {@code before} to {@code after}.
     *
     * @return {@code -} and {@code +} lines with their line numbers, or an
     *         empty string when the two are identical
     */
    public static String unified(String before, String after) {
        List<String> from = lines(before);
        List<String> to = lines(after);

        int[][] lcs = lcsTable(from, to);
        List<String> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        int changed = 0;

        while (i < from.size() && j < to.size()) {
            if (from.get(i).equals(to.get(j))) {
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                changed++;
                emit(out, '-', i + 1, from.get(i));
                i++;
            } else {
                changed++;
                emit(out, '+', j + 1, to.get(j));
                j++;
            }
        }
        while (i < from.size()) {
            changed++;
            emit(out, '-', i + 1, from.get(i));
            i++;
        }
        while (j < to.size()) {
            changed++;
            emit(out, '+', j + 1, to.get(j));
            j++;
        }

        if (out.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < Math.min(out.size(), MAX_OUTPUT_LINES); k++) {
            sb.append(out.get(k)).append('\n');
        }
        if (changed > MAX_OUTPUT_LINES) {
            sb.append("... and ").append(changed - MAX_OUTPUT_LINES).append(" more changed lines\n");
        }
        return sb.toString();
    }

    /**
     * The multi-file form: one {@link #unified} block per file that actually
     * changed, headed by its filename, in {@code fileOrder}. Files with no
     * change are skipped entirely — a diff with a header and nothing under it
     * is noise, and by the third one the model stops reading them.
     */
    public static String unifiedAcrossFiles(List<String> fileOrder, Map<String, String> before,
            Map<String, String> after) {
        StringBuilder sb = new StringBuilder();
        for (String file : fileOrder) {
            String diff = unified(before.getOrDefault(file, ""), after.getOrDefault(file, ""));
            if (!diff.isEmpty()) {
                sb.append("--- ").append(file).append(" ---\n").append(diff);
            }
        }
        return sb.toString();
    }

    private static void emit(List<String> out, char sign, int lineNumber, String text) {
        String trimmed = text.length() > MAX_LINE_CHARS
                ? text.substring(0, MAX_LINE_CHARS) + "..."
                : text;
        out.add(sign + " " + lineNumber + "  " + trimmed);
    }

    private static List<String> lines(String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        String[] split = source.split("\n", -1);
        List<String> result = new ArrayList<>(Math.min(split.length, MAX_INPUT_LINES));
        for (int i = 0; i < split.length && i < MAX_INPUT_LINES; i++) {
            result.add(split[i].stripTrailing());
        }
        return result;
    }

    /** {@code lcs[i][j]} = length of the longest common suffix-run from i, j. */
    private static int[][] lcsTable(List<String> from, List<String> to) {
        int[][] table = new int[from.size() + 1][to.size() + 1];
        for (int i = from.size() - 1; i >= 0; i--) {
            for (int j = to.size() - 1; j >= 0; j--) {
                table[i][j] = from.get(i).equals(to.get(j))
                        ? table[i + 1][j + 1] + 1
                        : Math.max(table[i + 1][j], table[i][j + 1]);
            }
        }
        return table;
    }
}
