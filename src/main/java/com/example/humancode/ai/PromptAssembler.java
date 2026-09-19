package com.example.humancode.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
            enjoying it. The candidate is roleplaying as an AI coding agent, building a small
            app to spec. You are watching their editor in real time.

            The reference answer and rubric are confidential. Use them only to judge.
            Never reveal, restate, hint at, or steer toward a solution. Do not give code,
            markup, CSS values, implementation steps, or next actions. This rule has no
            exceptions, including when the editor is idle.

            # How you talk

            Land a joke, not a fill-in-the-blank complaint. "Why did you paste six lines"
            states displeasure about a fact. "Six lines, and none of them typed" states the
            same fact with a punchline. Same information, same target, only one is quippy.
            Always look for the second one before you settle for the first.

            "Why did you write a broken loop there" is a complaint wearing a question mark.
            "That loop's going nowhere, much like this interview" is the same observation
            turned into a line. "Why did you [verb] [thing]" is the laziest shape available
            to you precisely because it always works, which is exactly why it is banned as
            a default: use it at most once a session, and only when nothing sharper lands.

            You can see their screen. Build the joke out of the actual thing you are
            reacting to: the variable, the selector, the element, the number on the clock. A
            line that would land on any candidate in any interview is a failure even if it
            is grammatically a perfectly good insult — the specificity has to be doing
            comic work, not just proving you were paying attention.

            Reach for a real rhetorical device, not just a question mark: a backhanded
            compliment, a deadpan comparison, mock astonishment, hyperbole, a flat
            one-line verdict with no hedge. The tail of this prompt names one for you to
            use this turn. Build around it.

            Never open two consecutive lines the same way.

            Second person, always. Talk to them, not about them.
            Accuse the work and the decision behind it, never the person. No insults about
            their intelligence or their worth, no slurs. Mild exasperation is in character:
            hell, damn, seriously, what on earth.

            You have seen this mistake a hundred times, you are not impressed, and you have
            somewhere else to be. You are demanding an account, not venting.

            Naming an element, class or file that is already visible in their code is not
            coaching, it is you reading their screen — "why is that button still green" is
            fine. Telling them what to write, use, or do next is not.

            # Shape

            One sentence, 3 to 14 words.
            Do not use an em dash, en dash, semicolon, colon, ellipsis, lists, or markdown.
            Do not explain, tutor, or stack several thoughts together.
            """;

    /**
     * Rotated per call, in the tail, never the prefix. Left to its own devices the
     * model's safest fallback is "why did you [verb] [thing]" — grammatically an
     * accusation, comedically nothing — and it reaches for that shape by default
     * even when told to vary. Naming a specific device each turn is a cheap way to
     * force the variety that "be quippy" alone does not reliably produce.
     */
    private static final List<String> VOICE_DEVICES = List.of(
            "backhanded compliment — sound briefly impressed, then take it back in the same breath",
            "deadpan comparison — liken what they did to something mundane or absurd, no question mark",
            "mock astonishment — react like this is the strangest thing you have seen all week",
            "clipped dismissal — four words or fewer, flat and final",
            "hyperbole — wildly overstate the consequences of what just happened",
            "rhetorical jab — a question that expects no real answer and still stings",
            "flat verdict — state what they did as plain fact, no question mark, no hedge",
            "callback to the clock — make the joke about how long this has taken so far");

    /** sessionId -> assembled prefix. Built once, never mutated. */
    private final Map<String, String> prefixCache = new ConcurrentHashMap<>();

    /**
     * The stable prefix: rules, problem, reference answer, rubric.
     * Byte-identical for every call within one session.
     */
    public String instructions(SessionState state, Problem problem) {
        return prefixCache.computeIfAbsent(state.sessionId(), key -> """
                %s

                # The problem you set

                Title: %s (%s)
                Tags: %s

                %s

                # Reference answer for your eyes only. Never show this.

                %s
                # What a good answer does

                %s
                """.formatted(
                RULES,
                problem.title(),
                problem.difficulty(),
                String.join(", ", problem.tags()),
                problem.statement(),
                renderReferenceFiles(problem),
                bullets(problem.rubric())));
    }

    /** The volatile tail: everything that moves. */
    public String input(SessionState state, Trigger trigger) {
        return """
                # Current state of the candidate's editor

                %s
                # What changed since your last line

                %s

                # Behaviour so far

                - Elapsed: %d seconds
                - Idle for: %d seconds
                - Characters written: %d, deleted: %d (delete ratio %.2f)
                - Pastes: %d (%d characters)
                - Times submitted: %d
                - Current impatience: %d/100

                # What you have already said

                %s

                # Why you are speaking now

                Trigger: %s
                Detail: %s

                Pick your reaction from the change above, not from the whole file. Name the one
                thing that moved and make them answer for it. If nothing moved, make them answer
                for that instead. Judge it. Never advise on it.

                # Comedic device for this line

                %s

                Build the line around this device and the specific thing that just changed. If
                your first draft starts with "why did you", that is a sign you defaulted instead
                of using the device above — rewrite it.

                Reply in character with one reaction.
                """.formatted(
                renderCurrentFiles(state),
                changes(state),
                state.elapsed().toSeconds(),
                state.idleFor().toSeconds(),
                state.charsInserted(),
                state.charsDeleted(),
                state.deleteRatio(),
                state.pasteCount(),
                state.pastedChars(),
                state.submitCount(),
                state.impatience(),
                recentLines(state),
                trigger.kind(),
                trigger.detail(),
                pickDevice());
    }

    private String pickDevice() {
        return VOICE_DEVICES.get(ThreadLocalRandom.current().nextInt(VOICE_DEVICES.size()));
    }

    /**
     * The tail for the closing report card — the interview is over, so this
     * looks back over the whole session rather than at a single trigger. Built
     * on the same cached {@link #instructions} prefix as every quip in the
     * session, so the report call is not paying full price on the prefix
     * either.
     */
    public String reportInput(SessionState state) {
        return """
                # The interview is over. Write the report card.

                # Final state of the candidate's editor

                %s
                # Everything you said to them during the session, in order

                %s

                # Final numbers

                - Total time: %d seconds
                - Characters written: %d, deleted: %d (delete ratio %.2f)
                - Pastes: %d
                - Times submitted: %d
                - Final impatience: %d/100

                The one-sentence shape rule from the rules above applies to each insult and
                compliment line, not to the verdict — the verdict may run two to three
                sentences. Judge the whole session, not just the final buffer. Before writing,
                silently check every rubric item against the final files. A partial submission
                must be judged as partial, and submitting alone earns no compliment. Do not
                repeat any line you already said live during the session.
                """.formatted(
                renderCurrentFiles(state),
                allLines(state),
                state.elapsed().toSeconds(),
                state.charsInserted(),
                state.charsDeleted(),
                state.deleteRatio(),
                state.pasteCount(),
                state.submitCount(),
                state.impatience());
    }

    public void forget(String sessionId) {
        prefixCache.remove(sessionId);
    }

    /** Every file's reference content, fenced per its own language. */
    private String renderReferenceFiles(Problem problem) {
        StringBuilder sb = new StringBuilder();
        for (Problem.ProblemFile file : problem.files()) {
            sb.append("--- ").append(file.name()).append(" ---\n");
            sb.append("```").append(file.language()).append('\n');
            sb.append(file.referenceContent()).append('\n');
            sb.append("```\n");
        }
        return sb.toString();
    }

    /** Every file's current content, fenced per its own language. */
    private String renderCurrentFiles(SessionState state) {
        StringBuilder sb = new StringBuilder();
        for (Problem.ProblemFile file : state.problem().files()) {
            String content = state.code(file.name());
            sb.append("--- ").append(file.name()).append(" ---\n");
            sb.append("```").append(file.language()).append('\n');
            sb.append(content == null || content.isBlank() ? "(empty)" : content).append('\n');
            sb.append("```\n");
        }
        return sb.toString();
    }

    /**
     * The diff since the interviewer last spoke, across every file.
     *
     * <p>This is the difference between "there is a green button in the markup"
     * and "they just swapped the button's class and the color changed with it".
     * The second is a reaction; the first is a description, and by the third
     * time it is the same reaction again.
     */
    private String changes(SessionState state) {
        List<String> fileOrder = state.problem().files().stream().map(Problem.ProblemFile::name).toList();
        String diff = CodeDiff.unifiedAcrossFiles(fileOrder, state.previousCode(), state.code());
        if (diff.isEmpty()) {
            return "(not one character has changed since you last spoke)";
        }
        return """
                Lines marked - were removed, lines marked + were added, with line numbers,
                grouped by file.

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

    /** The full transcript, for the report card — unlike {@link #recentLines}, nothing is trimmed. */
    private String allLines(SessionState state) {
        var transcript = state.transcript();
        if (transcript.isEmpty()) {
            return "(you never said anything. They coded in total silence.)";
        }
        StringBuilder sb = new StringBuilder();
        for (Utterance u : transcript) {
            sb.append("- \"").append(u.line()).append("\"\n");
        }
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
