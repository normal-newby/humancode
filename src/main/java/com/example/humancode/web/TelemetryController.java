package com.example.humancode.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.humancode.ai.Interviewer;
import com.example.humancode.interview.InterviewDirector;
import com.example.humancode.interview.SessionService;
import com.example.humancode.interview.SessionState;
import com.example.humancode.telemetry.EventType;
import com.example.humancode.telemetry.LineActivity;
import com.example.humancode.telemetry.TelemetryEvent;
import com.example.humancode.telemetry.TelemetryEventRepository;
import com.example.humancode.telemetry.TriggerEngine;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;

/**
 * Ingest for editor telemetry.
 *
 * <p>Hot path: keep it dumb and fast. It updates in-memory state, appends to the
 * replay log, and evaluates only the event-driven triggers (paste, meaningful
 * edit). Timer-driven triggers are the director's job.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/sessions/{id}")
public class TelemetryController {

    private final SessionService sessions;
    private final TelemetryEventRepository events;
    private final TriggerEngine triggers;
    private final InterviewDirector director;
    private final Interviewer interviewer;

    @PostMapping("/telemetry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Dtos.MetricsResponse ingest(@PathVariable String id, @Valid @RequestBody Dtos.TelemetryBatch batch) {
        SessionState state = sessions.require(id);
        Instant now = Instant.now();

        List<TelemetryEvent> toPersist = new ArrayList<>(batch.events().size());
        long pastedThisBatch = 0;
        long insertedThisBatch = 0;
        long deletedThisBatch = 0;

        // Snapshot each touched file's previous content before this batch
        // overwrites it, so completed-line detection can diff per file.
        Map<String, String> previousFiles = new HashMap<>();
        if (batch.files() != null) {
            for (String file : batch.files().keySet()) {
                previousFiles.put(file, state.code(file));
            }
        }

        for (Dtos.TelemetryItem item : batch.events()) {
            switch (item.type()) {
                case EDIT -> state.recordEdit(item.inserted(), item.deleted());
                case PASTE -> {
                    state.recordPaste(item.inserted());
                    pastedThisBatch += item.inserted();
                }
                case FOCUS, BLUR -> state.touch();
                case SUBMIT -> {
                    // Never sent as a batched item — SUBMIT is only ever
                    // constructed server-side in submit() below.
                }
            }
            if (item.type() == EventType.EDIT) {
                insertedThisBatch += item.inserted();
                deletedThisBatch += item.deleted();
            }
            toPersist.add(new TelemetryEvent(id, item.type(), now, item.inserted(), item.deleted(), item.detail()));
        }

        if (batch.files() != null) {
            batch.files().forEach(state::code);
        }
        events.saveAll(toPersist);

        // The interviewer noticing a paste as it happens is the whole joke, so
        // this one does not wait for the timer.
        if (pastedThisBatch > 0) {
            final long pasted = pastedThisBatch;
            triggers.onPaste(state, pasted).ifPresent(trigger -> director.fire(state, trigger));
        } else {
            // Whether they crossed a line boundary decides whether anything is
            // allowed to fire at all — see TriggerEngine.onMeaningfulEdit.
            LineActivity lines = LineActivity.between(previousFiles, batch.files());
            triggers.onMeaningfulEdit(state, insertedThisBatch, deletedThisBatch, lines)
                    .ifPresent(trigger -> director.fire(state, trigger));
        }

        return metrics(state);
    }

    /**
     * The candidate handed the turn back. There is no local verdict to report —
     * the interviewer judges the current diff against the rubric, same as every
     * other reaction (CLAUDE.md §6).
     */
    @PostMapping("/submit")
    public Dtos.MetricsResponse submit(@PathVariable String id) {
        SessionState state = sessions.require(id);
        state.recordSubmit();
        events.save(new TelemetryEvent(id, EventType.SUBMIT, Instant.now(), 0, 0,
                "Submission " + state.submitCount()));

        triggers.onSubmit(state).ifPresent(trigger -> director.fire(state, trigger));

        return metrics(state);
    }

    /**
     * The candidate asked for help directly. Unlike every other reaction in the
     * app this does not go through {@link TriggerEngine} or {@link InterviewDirector}
     * — it is not gated on a trigger firing or the quip cooldown, because the
     * candidate spending one of {@link SessionState#MAX_HINTS} is the gate. It
     * also never becomes an {@code Utterance}: it is a plain response to this
     * request, not a line in the transcript, so it cannot land mixed in with the
     * criticism log — the client renders it in a box of its own.
     */
    @PostMapping("/hint")
    public Dtos.HintResponse hint(@PathVariable String id) {
        SessionState state = sessions.require(id);
        int hintNumber = state.recordHint();
        events.save(new TelemetryEvent(id, EventType.HINT, Instant.now(), 0, 0,
                "Hint " + hintNumber + "/" + SessionState.MAX_HINTS));

        Interviewer.HintResult result = interviewer.hint(state, sessions.problemFor(state), hintNumber);

        return new Dtos.HintResponse(result.hint().text(), hintNumber, state.hintsRemaining(), result.canned());
    }

    private Dtos.MetricsResponse metrics(SessionState state) {
        return new Dtos.MetricsResponse(
                state.elapsed().toSeconds(),
                state.idleFor().toSeconds(),
                state.charsInserted(),
                state.charsDeleted(),
                state.deleteRatio(),
                state.pasteCount(),
                state.submitCount(),
                state.impatience());
    }
}
