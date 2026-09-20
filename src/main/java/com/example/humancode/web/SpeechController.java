package com.example.humancode.web;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.humancode.speech.SpeechService;

import lombok.RequiredArgsConstructor;

/**
 * Where the browser picks up a line's audio.
 *
 * <p>It is a separate fetch rather than bytes on the SSE channel because an
 * mp3 base64'd into an event is about a third bigger and rides the one stream
 * the interviewer speaks on — a slow clip would delay the line it belongs to.
 * The audio is already being made when this is called (see
 * {@link SpeechService}); this only waits for it.
 *
 * <p><b>A 404 here is normal and means silence, not failure.</b> No key
 * configured, synthesis failed, the clip was evicted, or it did not arrive in
 * time — all of them land here, and the client's job in every case is to let
 * the line be read rather than heard.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class SpeechController {

    /**
     * How long to hold the request open for a clip still in flight.
     *
     * <p><b>Sized for the problem statement, not for a heckle.</b> A one-line
     * quip synthesises in one to four seconds, but the statement is a
     * paragraph and was measured at 6.2 — so the 4s this started out as 404'd
     * the opening line of the session by a margin too small to notice in
     * testing and too large to ever work.
     *
     * <p>Holding the request open is cheap here and costs nothing in the cases
     * that matter: an id with no clip behind it (no key, evicted, already
     * failed) returns immediately rather than waiting, so only a synthesis
     * genuinely in flight ever occupies a thread. What this deliberately does
     * <em>not</em> do is decide how long anything on screen waits — the
     * client's own caps do that, and they are much shorter.
     */
    private static final Duration WAIT = Duration.ofSeconds(12);

    private final SpeechService speech;

    @GetMapping(path = "/speech/{id}", produces = "audio/mpeg")
    public ResponseEntity<byte[]> clip(@PathVariable String id) {
        return speech.await(id, WAIT)
                .map(audio -> ResponseEntity.ok()
                        .contentType(MediaType.valueOf("audio/mpeg"))
                        // Immutable by construction: one clip per utterance id,
                        // and an utterance is said once.
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                        .body(audio))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
