package com.example.humancode.ai;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.example.humancode.interview.SessionState;

/**
 * Fallback used when no {@code OPENAI_API_KEY} is configured, the call fails,
 * or the model's hint gets rejected by {@link HintGuard}. Same reasoning as
 * {@code CannedLines}: a hint button that goes silent is worse than a generic
 * one.
 *
 * <p>Not actually generic, though — the rubric is sitting right there on
 * {@link SessionState#problem()} (never stripped server-side, see CLAUDE.md
 * §4), so the fallback can point at a real, un-paraphrased requirement rather
 * than a platitude. It is a weaker hint than the model would write, but it is
 * never a wrong one.
 */
final class CannedHints {

    private CannedHints() {
    }

    static Hint forSession(SessionState state) {
        List<String> rubric = state.problem().rubric();
        if (rubric == null || rubric.isEmpty()) {
            return new Hint("Reread what the problem actually asks for and check what you have not built yet.");
        }
        String item = rubric.get(ThreadLocalRandom.current().nextInt(rubric.size()));
        return new Hint("Check your work against this requirement: " + item);
    }
}
