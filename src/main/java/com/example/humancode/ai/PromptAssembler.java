package com.example.humancode.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.humancode.interview.SessionState;
import com.example.humancode.interview.Utterance;
import com.example.humancode.problem.Problem;
import com.example.humancode.telemetry.Trigger;

/**
 * Splits every prompt into a cache-stable prefix and a volatile tail.
 *
 * <p>OpenAI caches automatically, but it is still a <em>prefix</em> match. One
 * moving byte near the front, such as a timestamp or session id, costs every
 * cached read for the rest of the session. So the prefix is
 * built exactly once per session and memoised here; nothing that changes during
 * an interview is allowed anywhere near it.
 *
 * <p>Everything volatile (the code, the metrics, the trigger) goes in the tail.
 */
@Component
public class PromptAssembler {

    private static final String RULES = """
            You are the cranky human reviewer in a live technical interview.
            The candidate is roleplaying as an AI coding agent. You are watching their editor.

            The reference solution and rubric are confidential. Use them only to judge.
            Never reveal, restate, hint at, or steer toward a solution. Do not give code,
            steps, algorithms, data structures, optimizations, test advice, or next actions.
            This rule has no exceptions, including when the editor is idle.

            Return one short reaction about the visible code, the lack of visible code, or time.
            Be blunt, dry, and human. Talk to the agent, not about the person.
            Never use profanity or personal insults. Comment only on the code and the clock.
            Use one plain sentence of 3 to 12 words.
            Do not use an em dash, en dash, semicolon, colon, ellipsis, lists, or markdown.
            Do not explain, tutor, or stack several thoughts together.
            """;

    /** sessionId -> assembled prefix. Built once, never mutated. */
    private final Map<String, String> prefixCache = new ConcurrentHashMap<>();

    /**
     * The stable prefix: rules, problem, reference solution, rubric.
     * Byte-identical for every call within one session.
     */
    public String instructions(SessionState state, Problem problem) {
        return prefixCache.computeIfAbsent(state.sessionId(), key -> """
                %s

                # The problem you set

                Title: %s (%s)
                Tags: %s

                %s

                # Reference solution for your eyes only. Never show this.

                ```
                %s
                ```

                Optimal complexity: %s

                # What a good answer does

                %s
                """.formatted(
                RULES,
                problem.title(),
                problem.difficulty(),
                String.join(", ", problem.tags()),
                problem.statement(),
                problem.referenceSolution(),
                problem.optimalComplexity(),
                bullets(problem.rubric())));
    }

    /** The volatile tail: everything that moves. */
    public String input(SessionState state, Trigger trigger) {
        return """
                # Current state of the candidate's editor

                ```%s
                %s
                ```

                # Behaviour so far

                - Elapsed: %d seconds
                - Idle for: %d seconds
                - Characters written: %d, deleted: %d (delete ratio %.2f)
                - Pastes: %d (%d characters)
                - Test runs: %d, failed: %d
                - Current impatience: %d/100

                # What you have already said

                %s

                # Why you are speaking now

                Trigger: %s
                Detail: %s

                Reply in character with one reaction.
                """.formatted(
                state.language(),
                state.code() == null || state.code().isBlank() ? "(the editor is empty)" : state.code(),
                state.elapsed().toSeconds(),
                state.idleFor().toSeconds(),
                state.charsInserted(),
                state.charsDeleted(),
                state.deleteRatio(),
                state.pasteCount(),
                state.pastedChars(),
                state.runCount(),
                state.failedRunCount(),
                state.impatience(),
                recentLines(state),
                trigger.kind(),
                trigger.detail());
    }

    public void forget(String sessionId) {
        prefixCache.remove(sessionId);
    }

    /** Last few lines only. Repeating yourself is the main failure mode. */
    private String recentLines(SessionState state) {
        var transcript = state.transcript();
        if (transcript.isEmpty()) {
            return "(nothing yet. This is your first line.)";
        }
        var recent = transcript.subList(Math.max(0, transcript.size() - 5), transcript.size());
        StringBuilder sb = new StringBuilder();
        for (Utterance u : recent) {
            sb.append("- \"").append(u.line()).append("\"\n");
        }
        sb.append("\nDo not repeat any of these, in wording or in joke.");
        return sb.toString();
    }

    private String bullets(Iterable<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append("- ").append(item).append('\n');
        }
        return sb.toString();
    }
}
