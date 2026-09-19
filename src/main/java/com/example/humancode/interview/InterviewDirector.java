package com.example.humancode.interview;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.humancode.ai.Interviewer;
import com.example.humancode.ai.Reaction;
import com.example.humancode.telemetry.Trigger;
import com.example.humancode.telemetry.TriggerEngine;
import com.example.humancode.web.SseHub;

/**
 * Joins the three halves of the loop: the {@link TriggerEngine} decides when,
 * the {@link Interviewer} decides what, and {@link SseHub} delivers it.
 *
 * <p>This is the only place that turns a trigger into a spoken line, so the
 * cooldown and impatience rules live here rather than being scattered.
 */
@Component
public class InterviewDirector {

    private static final Logger log = LoggerFactory.getLogger(InterviewDirector.class);

    private final SessionService sessions;
    private final TriggerEngine triggers;
    private final Interviewer interviewer;
    private final SseHub sse;

    public InterviewDirector(SessionService sessions, TriggerEngine triggers,
            Interviewer interviewer, SseHub sse) {
        this.sessions = sessions;
        this.triggers = triggers;
        this.interviewer = interviewer;
        this.sse = sse;
    }

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

        Interviewer.Result result = interviewer.react(state, sessions.problemFor(state), trigger);
        Reaction reaction = result.reaction();

        int impatience = state.bumpImpatience(reaction.impatienceDelta());

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

        log.info("[{}] {} -> \"{}\" (impatience {}{})", state.sessionId(), trigger.kind(),
                reaction.line(), impatience, result.canned() ? ", canned" : "");
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
