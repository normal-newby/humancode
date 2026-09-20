package com.example.humancode.speech;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.example.humancode.ai.Reaction.Mood;
import com.example.humancode.config.HumancodeProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import tools.jackson.databind.ObjectMapper;

/**
 * The interviewer's voice.
 *
 * <p>Same shape as every other outbound call in this app, and for the same
 * reasons (CLAUDE.md §5.1): <b>it never throws, it never blocks the loop, and
 * with no key configured the app is simply silent.</b> A session that goes quiet
 * because a synthesis failed is a session that still works; a session that
 * stalls waiting for audio is a broken demo.
 *
 * <p><b>Synthesis starts before the line is pushed over SSE, not when the
 * browser asks for it.</b> That head start is the whole latency budget: the
 * client holds a new line behind an 850ms typing indicator (UI-DESIGN.md §4.4)
 * and then reveals it over ~2.8s, and v3 comes back in about a second, so the
 * clip is normally waiting by the time anyone can hear it. Fetch-on-demand
 * would spend that second in silence with the text already on screen.
 *
 * <p>Raw {@code java.net.http.HttpClient} rather than a vendor SDK: this is one
 * POST that returns audio bytes, and the SDK would be a dependency, a Jackson
 * version to worry about (CLAUDE.md §5 — there are already two) and a wrapper
 * around exactly this.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SpeechService {

    private static final String ENDPOINT = "https://api.elevenlabs.io/v1/text-to-speech/";

    /**
     * Audio tags are a v3 feature. On any other model the bracketed text is
     * read out loud, so {@link Delivery}'s tags are dropped rather than
     * shipped — see the note on {@code humancode.speech.model}.
     */
    private static final String TAG_CAPABLE_PREFIX = "eleven_v3";

    private final HumancodeProperties props;
    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** utterance id -> the clip being made for it. */
    private final Map<String, Clip> clips = new ConcurrentHashMap<>();

    private record Clip(String sessionId, Instant at, CompletableFuture<byte[]> audio) {
    }

    @PostConstruct
    void announce() {
        HumancodeProperties.Speech speech = props.speech();
        boolean hasKey = speech != null && speech.apiKey() != null && !speech.apiKey().isBlank();
        if (!configured()) {
            // The two silent states are told apart on purpose. "Key is not
            // set" printed while a key was set and merely switched off is a
            // false lead, and this feature has already cost one debugging
            // session to a misleading signal.
            log.warn(hasKey
                    ? "Speech is switched off (humancode.speech.enabled=false) — the interviewer will be silent"
                    : "ELEVENLABS_API_KEY is not set — the interviewer will be silent");
            return;
        }
        log.info("Speech ready (voice={}, model={}, tags={})",
                props.speech().voiceId(), props.speech().model(),
                tagsSupported() ? "on" : "off (model is not v3)");
    }

    public boolean configured() {
        HumancodeProperties.Speech speech = props.speech();
        return speech != null && speech.enabled()
                && speech.apiKey() != null && !speech.apiKey().isBlank();
    }

    private boolean tagsSupported() {
        return props.speech().model().startsWith(TAG_CAPABLE_PREFIX);
    }

    /**
     * Starts synthesising a line. Returns immediately; call {@link #await} for
     * the bytes.
     *
     * @param utteranceId what the client will ask for the clip by
     */
    public void prepare(String utteranceId, String sessionId, String line, Mood mood, int impatience) {
        speak(utteranceId, sessionId, line, Delivery.forLine(mood, impatience));
    }

    /** The closing verdict, whose delivery comes off the meter — see {@link Delivery#forImpatience}. */
    public void prepareVerdict(String speechId, String sessionId, String line, int impatience) {
        speak(speechId, sessionId, line, Delivery.forImpatience(impatience));
    }

    /**
     * The problem, read out as it is set.
     *
     * <p>Deliberately {@code NEUTRAL} at impatience zero, which is
     * {@link Delivery#FLAT}: it is the same judgement the pinned prompt's face
     * already makes (UI-DESIGN.md §6a — "they have not seen a line of your code
     * when they set the problem"), and the voice and the face disagreeing about
     * the opening beat would be the first thing anyone noticed.
     *
     * <p>Started at session creation, which buys the longest head start
     * anywhere in the app: the prelude runs about 1.9 seconds after this before
     * the statement is on screen at all (§4.8a).
     *
     * @return the id the browser fetches the clip by, or null when running silent
     */
    public String prepareStatement(String sessionId, String statement) {
        if (!configured()) {
            return null;
        }
        String id = "statement-" + sessionId;
        speak(id, sessionId, statement, Delivery.forLine(Mood.NEUTRAL, 0));
        return id;
    }

    private void speak(String id, String sessionId, String line, Delivery delivery) {
        if (!configured() || line == null || line.isBlank()) {
            return;
        }
        evictIfFull();
        long startedAt = System.nanoTime();
        CompletableFuture<byte[]> audio = synthesise(line, delivery)
                .whenComplete((bytes, error) -> {
                    long ms = (System.nanoTime() - startedAt) / 1_000_000;
                    if (error != null) {
                        log.warn("Speech failed for session {} after {}ms ({}): {}",
                                sessionId, ms, delivery, error.toString());
                    } else {
                        log.info("Speech for session {}: {} {} -> {} bytes in {}ms",
                                sessionId, delivery, delivery.tag(), bytes.length, ms);
                    }
                });
        clips.put(id, new Clip(sessionId, Instant.now(), audio));
    }

    private CompletableFuture<byte[]> synthesise(String line, Delivery delivery) {
        String spoken = tagsSupported() ? delivery.apply(line) : line;

        // Built through Jackson rather than string-concatenated: a line is
        // model output and will eventually contain a quote or a backslash.
        Map<String, Object> body = Map.of(
                "text", spoken,
                "model_id", props.speech().model(),
                "voice_settings", Map.of(
                        "stability", delivery.stability(),
                        "similarity_boost", 0.75,
                        "style", delivery.style(),
                        "use_speaker_boost", true));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + props.speech().voiceId()))
                .timeout(props.speech().requestTimeout())
                .header("xi-api-key", props.speech().apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        // The error body is JSON even though we asked for audio,
                        // and it is the only place a wrong voice id or a missing
                        // permission ever says so.
                        throw new IllegalStateException("ElevenLabs returned " + response.statusCode()
                                + ": " + new String(response.body()).substring(
                                        0, Math.min(200, response.body().length)));
                    }
                    return response.body();
                });
    }

    /**
     * The clip for an utterance, if it exists and arrives in time.
     *
     * <p>Waits, rather than returning what is ready: the browser asks the
     * moment the line lands and the call is usually already in flight. The
     * deadline is short and a miss is a 404, which the client treats as "this
     * line is silent" — never as an error worth showing anyone.
     */
    public Optional<byte[]> await(String id, Duration limit) {
        Clip clip = clips.get(id);
        if (clip == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(clip.audio().get(limit.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            // Already logged with its cause in speak(); this is the read side
            // finding out about it.
            return Optional.empty();
        }
    }

    /**
     * Oldest first, once over the cap, and <b>this is the only eviction there
     * is</b>.
     *
     * <p>The obvious alternative — drop a session's clips when it ends, the way
     * {@code PromptAssembler.forget} does — is wrong here and quietly so: the
     * closing verdict is synthesised during {@code /finish} and fetched by the
     * report card <em>after</em> that call has returned, so a tidy-up on session
     * end would silence the one line in the session that matters most. A cap is
     * enough anyway; clips are tens of kilobytes and a session produces a
     * handful.
     */
    private void evictIfFull() {
        // Floor of 1, and bail when there is nothing left to drop: a
        // misconfigured cap of 0, or a concurrent forget() emptying the map,
        // would otherwise spin this loop forever.
        int max = Math.max(1, props.speech().maxClips());
        while (clips.size() >= max) {
            Optional<String> oldest = clips.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().at()))
                    .map(Map.Entry::getKey);
            if (oldest.isEmpty()) {
                return;
            }
            clips.remove(oldest.get());
        }
    }
}
