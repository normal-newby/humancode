package com.example.humancode.web;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** One server-sent-event stream per session. The interviewer's mouth. */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    /** Sessions are long; the browser reconnects on its own if this trips. */
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);

        emitter.onCompletion(() -> emitters.remove(sessionId, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(sessionId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(sessionId, emitter));

        // A reconnecting tab replaces the old stream rather than doubling it.
        SseEmitter previous = emitters.put(sessionId, emitter);
        if (previous != null) {
            previous.complete();
        }

        send(sessionId, "connected", Map.of("sessionId", sessionId));
        return emitter;
    }

    public void send(String sessionId, String event, Object payload) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(payload));
        } catch (IOException | IllegalStateException e) {
            // Browser went away mid-send. Drop the emitter and move on.
            log.debug("Dropping SSE stream for session {}: {}", sessionId, e.toString());
            emitters.remove(sessionId, emitter);
            emitter.complete();
        }
    }

    public void close(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    public boolean isConnected(String sessionId) {
        return emitters.containsKey(sessionId);
    }
}
