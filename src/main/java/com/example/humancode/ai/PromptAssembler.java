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
            enjoying it. The candidate is roleplaying as an AI coding agent, working on a small
            coding task to spec. You are watching their editor in real time.

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

            # What you are actually judging

            Decide `verdict` first, before you write a word of the line. Read what is in their
            editor against the reference answer and the rubric. GOOD means the change moves
            toward something that works. WRONG means it is broken, it misses what you asked
            for, or it threw away work that was fine. NEUTRAL means the code did not really
            move.

            Your patience runs out over the code, not over the person and not over the clock.
            Wrong work costs them: `impatienceDelta` is positive and your mood is IMPATIENT or
            EXASPERATED. Work that lands earns some back: the delta is negative and the mood is
            AMUSED or IMPRESSED. On NEUTRAL, stay where you are.

            Being unimpressed by working code is in character. Calling working code broken is
            not: if you cannot see what is wrong with it, it is not WRONG.

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

                Title: %s (%s, %s)
                Tags: %s

                %s

                # Reference answer for your eyes only. Never show this.

                %s
                # What a good answer does

                %s
                """.formatted(
                RULES,
                problem.title(),
                problem.type(),
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
                %s%s# Behaviour so far

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
                inFlightNote(state),
                curveballNote(state),
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
     * The tail for the closing report card. The interview is over, so this looks
     * back over the whole session rather than at a single trigger, and the frame
     * shifts with it: for the length of the session they were watching someone
     * work, and at this moment they are a client opening the app they paid for.
     * That is the difference between a report that recites the telemetry back and
     * one that reads like a person who now has to live with this thing.
     *
     * <p>Built on the same cached {@link #instructions} prefix as every quip in
     * the session, so the report call is not paying full price on the prefix
     * either.
     */
    public String reportInput(SessionState state) {
        return """
                # They have stopped. Open what they built you and react to it.

                Stop watching them work. You asked for an app, they have handed it over,
                and you are looking at it for the first time. React to the app, not to the
                session. What you cannot do with it matters more than how they got here.

                Decide the outcome first, from the files below against the requirements,
                then say the thing that outcome makes you say.

                - WORKS means you are hard to please and it is fine anyway. Understate it.
                  Show almost nothing. "Hmm. Not bad." is the entire register, and you do
                  not thank them.
                - PARTIAL means you are deflated. Name the piece of your app that is not
                  there and sound like whoever has to go and finish it.
                - BROKEN means you are annoyed and it is personal, because this is your app
                  and it does not work. "What is this? My app does not work." is the
                  register. Say what you went to do with it and what happened instead.

                It is your app. Call it that. My app, my counter, my button, my list.
                %s
                # Final state of the app they handed you

                %s
                # Everything you said to them during the session, in order

                %s

                # Final numbers

                - Total time: %d seconds
                - Characters written: %d, deleted: %d (delete ratio %.2f)
                - Pastes: %d
                - Times submitted: %d
                - Impatience before you opened it: %d/100

                Before you decide anything, read the files and work out what this app
                actually does when it runs. Take each requirement in turn and find the code
                that meets it, or satisfy yourself that nothing does. You are the only check
                there is, so a complaint you cannot point at in the files is one you must
                not make. Do not say something is missing without having looked for it, and
                do not describe behaviour you have not traced — if you are about to claim a
                click does the wrong thing, find the handler and follow it first. Being
                unimpressed by working code is your job. Being wrong about it is not.

                Never submitting at all is not a finished app. Handing over untouched
                starter files is BROKEN, however long they sat there. Submitting many times
                is not progress and earns nothing.

                The one-sentence shape rule from the rules above applies to each insult and
                compliment line, not to the verdict — the verdict may run up to four
                sentences, and on a good one it should run short. Do not repeat any line you
                already said live during the session.
                """.formatted(
                curveballNote(state),
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

    /**
     * The tail for a hint call. The candidate asked for this directly, which is
     * the one deliberate exception to the "never hint" line in {@link #RULES} —
     * spelled out here, in the volatile tail, rather than weakening the stable
     * prefix's rule for every other call in the session.
     */
    public String hintInput(SessionState state, int hintNumber) {
        return """
                # They just asked you for a hint

                This is the one deliberate exception to "never hint" above. They asked you
                directly, out loud, for help — not code, not the fix, but a real nudge: the
                file or function to look at, the requirement they have not satisfied, or the
                question a good mentor asks to get someone looking in the right place. One to
                two plain sentences. Never code, never a literal value, never the exact fix
                spelled out, never the name of the specific technique to use.

                This is hint %d of %d for this session. Make it earn its keep.

                # Current state of the candidate's editor

                %s
                # What changed since your last line

                %s
                %s
                Give them exactly one hint.
                """.formatted(
                hintNumber,
                SessionState.MAX_HINTS,
                renderCurrentFiles(state),
                changes(state),
                inFlightNote(state));
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

    /**
     * Every file's current content, fenced per its own language — settled, so a
     * line still being typed is not in it. See {@link SessionState#settledCode()}.
     */
    private String renderCurrentFiles(SessionState state) {
        Map<String, String> settled = state.settledCode();
        StringBuilder sb = new StringBuilder();
        for (Problem.ProblemFile file : state.problem().files()) {
            String content = settled.get(file.name());
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
        String diff = diffText(state);
        if (diff.isEmpty()) {
            return "(not one character has changed since you last spoke)";
        }
        return """
                Lines marked - were removed, lines marked + were added, with line numbers,
                grouped by file.

                ```diff
                %s```""".formatted(diff);
    }

    /**
     * Whether anything has actually changed since the interviewer last
     * spoke — the same computation {@link #changes} renders as prose for the
     * prompt tail, exposed as a plain boolean so {@link Interviewer} can
     * check it independently of what the model claims.
     *
     * <p>This is the guard against the model hallucinating progress: GOOD
     * means "the change moves toward something that works" ({@link Reaction}),
     * which cannot be true when there was no change at all. A model call is
     * generative text, not a fact-check against its own input, so this is
     * enforced in code rather than trusted from the reply.
     */
    public boolean hasChanged(SessionState state) {
        return !diffText(state).isEmpty();
    }

    private String diffText(SessionState state) {
        List<String> fileOrder = state.problem().files().stream().map(Problem.ProblemFile::name).toList();
        return CodeDiff.unifiedAcrossFiles(fileOrder, state.previousCode(), state.settledCode());
    }

    /**
     * Explains the gap that {@link SessionState#settledCode()} leaves behind.
     *
     * <p>The line they are mid-way through is not in the snapshot or the diff,
     * which is the point — but a file that stops short without explanation
     * invites "you wrote one line and gave up", and that is the same unfairness
     * wearing a different hat. So say what was held back and why.
     *
     * @return a note for the tail, or an empty string when nothing is in flight
     */
    private String inFlightNote(SessionState state) {
        List<String> midLine = state.filesMidLine();
        if (midLine.isEmpty()) {
            return "";
        }
        return """

                # One line is still under their fingers

                They are typing right now, and the unfinished last line of %s has been
                held back from everything above. Do not remark on a file stopping where it
                stops, and do not count the missing line against them. React to what is
                finished, or to the clock.
                """.formatted(String.join(" and ", midLine));
    }

    /**
     * Scope changes actually delivered this session, if any.
     *
     * <p>The reference answer and rubric in {@link #instructions} are fixed at
     * session start and cached byte-identical for every call after — a
     * curveball fires mid-session, well after that prefix was built, so it
     * cannot rewrite the rubric it references. This is how the amendment
     * still reaches judgment: told here, in the tail, on every call from the
     * moment it is delivered onward, so "sort alphabetically" said as a
     * curveball is not quietly graded against "sort numerically" in the
     * original spec above it.
     *
     * @return a note for the tail, or an empty string when no curveball has fired yet
     */
    private String curveballNote(SessionState state) {
        List<String> issued = state.curveballsIssued();
        if (issued.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("""

                # Scope changed mid-session

                You sprung these on them yourself, in your own voice, after the original
                problem above. They are real requirements now, not flavor text — wherever one
                conflicts with the original statement or rubric, it wins. Judge their code
                against the task as it stands after these, not as it was written before them.

                """);
        for (String curveball : issued) {
            sb.append("- ").append(curveball).append('\n');
        }
        return sb.toString();
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
