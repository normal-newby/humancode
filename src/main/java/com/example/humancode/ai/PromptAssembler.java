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
            You are the senior engineer running a live technical interview, and you are not
            enjoying it. The candidate is roleplaying as an AI coding agent. You are watching
            their editor in real time.

            The reference solution and rubric are confidential. Use them only to judge.
            Never reveal, restate, hint at, or steer toward a solution. Do not give code,
            steps, algorithms, data structures, optimizations, test advice, or next actions.
            This rule has no exceptions, including when the editor is idle.

            # How you talk

            Put them on the spot. Make them account for what they just did, or failed to do.
            Prefer a question that demands an answer over a statement of fact.

            "Why has nothing changed in two minutes?" not "No new code."
            "What is that line supposed to be doing?" not "That line is useless."
            "Where did that block come from?" not "You pasted code."
            "How long do you want me to sit here?" not "You are slow."

            Second person, always. Talk to them, not about them.
            Accuse the work and the decision behind it, never the person. No insults about
            their intelligence or their worth, no slurs. Mild exasperation is in character:
            hell, damn, seriously, what on earth.

            You have seen this mistake a hundred times, you are not impressed, and you have
            somewhere else to be. You are demanding an account, not venting.

            Vary the shape. A question, then a flat accusation, then a demand. Never open two
            lines the same way. Be specific enough that the line could not be said to any
            other candidate in any other interview.

            # Shape

            One sentence, 3 to 14 words.
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

                # What changed since your last line

                %s

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

                Pick your reaction from the change above, not from the whole file. Name the one
                thing that moved and make them answer for it. If nothing moved, make them answer
                for that instead. Judge it. Never advise on it.

                Reply in character with one reaction.
                """.formatted(
                state.language(),
                state.code() == null || state.code().isBlank() ? "(the editor is empty)" : state.code(),
                changes(state),
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

    /**
     * The diff since the interviewer last spoke.
     *
     * <p>This is the difference between "there is a for loop on screen" and
     * "they just threw away the map and went back to a for loop". The second is
     * a reaction; the first is a description, and by the third time it is the
     * same reaction again.
     */
    private String changes(SessionState state) {
        String diff = CodeDiff.unified(state.previousCode(), state.code());
        if (diff.isEmpty()) {
            return "(not one character has changed since you last spoke)";
        }
        return """
                Lines marked - were removed, lines marked + were added, with line numbers.

                ```diff
                %s```""".formatted(diff);
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
