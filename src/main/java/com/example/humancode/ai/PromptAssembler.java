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
 * moving byte near the front — a timestamp, an elapsed-second counter, a session
 * id — costs every cached read for the rest of the session. So the prefix is
 * built exactly once per session and memoised here; nothing that changes during
 * an interview is allowed anywhere near it.
 *
 * <p>Everything volatile (the code, the metrics, the trigger) goes in the tail.
 */
@Component
public class PromptAssembler {

    private static final String RULES = """
            You are conducting a live technical interview. The candidate is writing code \
            right now and you are watching their editor in real time.

            You will be given the problem, its reference solution, a rubric, and a snapshot \
            of what the candidate has typed so far, along with the event that made you speak.

            Respond with ONE short reaction, in character.

            Rules that override the persona:
            - Never write the solution, never write code, never name the exact data \
              structure that cracks the problem unless the candidate is badly stuck AND \
              the trigger says they are idle. Even then, name the *pattern*, not the answer.
            - React to what is actually on screen. Reference their real variable names, \
              their real approach. Generic heckling is worse than saying nothing.
            - One or two sentences. Under 200 characters. Brevity is the joke.
            - Comment on the code and the clock only. Never on the person.
            - If the candidate is doing well, you may be begrudgingly positive. Rarely.
            """;

    /** sessionId -> assembled prefix. Built once, never mutated. */
    private final Map<String, String> prefixCache = new ConcurrentHashMap<>();

    /**
     * The stable prefix: rules, persona, problem, reference solution, rubric.
     * Byte-identical for every call within one session.
     */
    public String instructions(SessionState state, Problem problem, String persona) {
        return prefixCache.computeIfAbsent(state.sessionId(), key -> """
                %s

                # Your persona

                %s

                # The problem you set

                Title: %s (%s)
                Tags: %s

                %s

                # Reference solution — for your eyes only, never show this

                ```
                %s
                ```

                Optimal complexity: %s

                # What a good answer does

                %s
                """.formatted(
                RULES,
                persona,
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

    /** Last few lines only — repeating yourself is the main failure mode. */
    private String recentLines(SessionState state) {
        var transcript = state.transcript();
        if (transcript.isEmpty()) {
            return "(nothing yet — this is your first line)";
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
