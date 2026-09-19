package com.example.humancode.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.humancode.interview.InterviewDirector;
import com.example.humancode.interview.SessionService;
import com.example.humancode.interview.SessionState;
import com.example.humancode.telemetry.EventType;
import com.example.humancode.telemetry.TelemetryEvent;
import com.example.humancode.telemetry.TelemetryEventRepository;
import com.example.humancode.telemetry.TriggerEngine;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;

/**
 * Ingest for editor telemetry.
 *
 * <p>Hot path: keep it dumb and fast. It updates in-memory state, appends to the
 * replay log, and evaluates only the event-driven triggers (paste, test run).
 * Timer-driven triggers are the director's job.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/sessions/{id}")
public class TelemetryController {

    private final SessionService sessions;
    private final TelemetryEventRepository events;
    private final TriggerEngine triggers;
    private final InterviewDirector director;

    @PostMapping("/telemetry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Dtos.MetricsResponse ingest(@PathVariable String id, @Valid @RequestBody Dtos.TelemetryBatch batch) {
        SessionState state = sessions.require(id);
        Instant now = Instant.now();

        List<TelemetryEvent> toPersist = new ArrayList<>(batch.events().size());
        long pastedThisBatch = 0;

        for (Dtos.TelemetryItem item : batch.events()) {
            switch (item.type()) {
                case EDIT -> state.recordEdit(item.inserted(), item.deleted());
                case PASTE -> {
                    state.recordPaste(item.inserted());
                    pastedThisBatch += item.inserted();
                }
                case FOCUS, BLUR, RUN -> state.touch();
            }
            toPersist.add(new TelemetryEvent(id, item.type(), now, item.inserted(), item.deleted(), item.detail()));
        }

        if (batch.code() != null) {
            state.code(batch.code());
        }
        events.saveAll(toPersist);

        // The interviewer noticing a paste as it happens is the whole joke, so
        // this one does not wait for the timer.
        if (pastedThisBatch > 0) {
            final long pasted = pastedThisBatch;
            triggers.onPaste(state, pasted).ifPresent(trigger -> director.fire(state, trigger));
        }

        return metrics(state);
    }

    @PostMapping("/run")
    public Dtos.MetricsResponse run(@PathVariable String id, @Valid @RequestBody Dtos.RunResultRequest request) {
        SessionState state = sessions.require(id);
        state.recordRun(request.passed());
        events.save(new TelemetryEvent(id, EventType.RUN, Instant.now(), 0, 0, request.summary()));

        triggers.onRun(state, request.passed(), request.summary())
                .ifPresent(trigger -> director.fire(state, trigger));

        return metrics(state);
    }

    private Dtos.MetricsResponse metrics(SessionState state) {
        return new Dtos.MetricsResponse(
                state.elapsed().toSeconds(),
                state.idleFor().toSeconds(),
                state.charsInserted(),
                state.charsDeleted(),
                state.deleteRatio(),
                state.pasteCount(),
                state.runCount(),
                state.failedRunCount(),
                state.impatience());
    }
}
