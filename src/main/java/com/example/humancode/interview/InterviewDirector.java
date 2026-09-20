package com.example.humancode.interview;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.humancode.ai.Interviewer;
import com.example.humancode.ai.Reaction;
import com.example.humancode.telemetry.Trigger;
import com.example.humancode.telemetry.TriggerEngine;
import com.example.humancode.web.SseHub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Joins the three halves of the loop: the {@link TriggerEngine} decides when,
 * the {@link Interviewer} decides what, and {@link SseHub} delivers it.
 *
 * <p>This is the only place that turns a trigger into a spoken line, so the
 * cooldown and impatience rules live here rather than being scattered.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class InterviewDirector {

    private final SessionService sessions;
    private final TriggerEngine triggers;
    private final Interviewer interviewer;
    private final SseHub sse;

    /**
     * The heartbeat. Cheap: it reads in-memory state and almost always finds
     * nothing to do. No model call happens unless a rule actually fires.
     */
    @Scheduled(fixedDelay = 2000)
    public void tick() {
        for (SessionState state : sessions.active()) {
            try {
                triggers.evaluate(state).ifPresent(trigger -> fire(state, trigger));
            } catch (RuntimeException e) {
                log.warn("Trigger evaluation failed for session {}", state.sessionId(), e);
            }
        }
    }

    /** Fire a trigger now, respecting the cooldown unless it is immediate. */
    public Optional<Utterance> fire(SessionState state, Trigger trigger) {
        if (trigger.cooldown() && !triggers.cooledDown(state)) {
            log.trace("Suppressed {} for session {} — still cooling down", trigger.kind(), state.sessionId());
            return Optional.empty();
        }
        if (!sse.isConnected(state.sessionId())) {
            // Nobody is listening; do not spend a model call on an empty room.
            return Optional.empty();
        }

        // Recorded here, not inside Interviewer: a curveball is delivered
        // verbatim with no model call at all (CLAUDE.md §2), so this is the
        // only place that knows it actually went out. From the next call on —
        // the very next quip, or the closing report card — PromptAssembler
        // reads this back so the amended scope is judged against, not the
        // original rubric alone. Deliberately after both guards above: a
        // suppressed trigger must not start being graded on a change the
        // candidate never actually saw.
        if (trigger.kind() == Trigger.Kind.CURVEBALL) {
            state.recordCurveball(trigger.detail());
        }

        Interviewer.Result result = interviewer.react(state, sessions.problemFor(state), trigger);
        Reaction reaction = result.reaction();

        // The line has been written against this buffer, so the next one should
        // only see what happens after it. Deliberately after react() and after
        // both guards above: a suppressed trigger costs no call and must not
        // eat the diff either.
        state.markCodeSpokenFor();

        // alignedDelta, not impatienceDelta: the meter answers to the verdict
        // on the code, so wrong work costs them and good work earns some back
        // even when the model returns a number that disagrees with itself.
        int impatience = state.bumpImpatience(reaction.alignedDelta());

        Utterance utterance = new Utterance(
                UUID.randomUUID().toString(),
                Instant.now(),
                trigger.kind().name(),
                reaction.line(),
                reaction.mood().name(),
                impatience,
                result.canned());
        state.addUtterance(utterance);

        if (reaction.note() != null && !reaction.note().isBlank() && state.addNote(reaction.note())) {
            sse.send(state.sessionId(), "note", new NotePayload(reaction.note(), Instant.now()));
        }

        sse.send(state.sessionId(), "utterance", utterance);
        sse.send(state.sessionId(), "meter", new MeterPayload(impatience, reaction.mood().name()));

        log.info("[{}] {} -> \"{}\" (verdict {}, mood {}, delta {} -> impatience {}{})",
                state.sessionId(), trigger.kind(), reaction.line(), reaction.verdict(),
                reaction.mood(), reaction.alignedDelta(), impatience,
                result.canned() ? ", canned" : "");
        return Optional.of(utterance);
    }

    public void pushPhase(SessionState state, Phase phase) {
        state.phase(phase);
        sessions.snapshot(state);
        sse.send(state.sessionId(), "phase", new PhasePayload(phase.name()));
    }

    public record MeterPayload(int impatience, String mood) {
    }

    public record NotePayload(String note, Instant at) {
    }

    public record PhasePayload(String phase) {
    }
}
