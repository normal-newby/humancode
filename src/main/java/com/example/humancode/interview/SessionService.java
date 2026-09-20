package com.example.humancode.interview;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.humancode.problem.Difficulty;
import com.example.humancode.problem.Problem;
import com.example.humancode.problem.ProblemSource;
import com.example.humancode.problem.ProblemType;
import com.example.humancode.problem.ProblemRuntime;
import com.example.humancode.speech.SpeechService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;

import tools.jackson.databind.ObjectMapper;

/**
 * Owns session lifecycle and the in-memory {@link SessionState} map.
 *
 * <p>Live state is in memory; SQLite gets a snapshot on phase transitions and at
 * session end.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SessionService {

    private final Map<String, SessionState> live = new ConcurrentHashMap<>();
    private final SessionRepository repository;
    private final ProblemSource problems;
    private final SpeechService speech;
    private final ObjectMapper mapper;

    @PostConstruct
    void announceSource() {
        log.info("Problem source: {}", problems.describe());
    }

    @Transactional
    public SessionState start(String problemId, String language, Difficulty difficulty) {
        return start(problemId, language, difficulty, null);
    }

    public SessionState start(String problemId, String language, Difficulty difficulty, ProblemType type) {
        return start(problemId, language, difficulty, type, null);
    }

    public SessionState start(String problemId, String language, Difficulty difficulty, ProblemType type,
            ProblemRuntime runtime) {
        return start(problemId, language, difficulty, type, runtime, null);
    }

    /**
     * @param userId the candidate this session counts for, or null for an
     *               anonymous one. Already verified by the caller — nothing here
     *               trusts a handle, and a session that cannot prove who it
     *               belongs to simply belongs to nobody.
     */
    public SessionState start(String problemId, String language, Difficulty difficulty, ProblemType type,
            ProblemRuntime runtime, String userId) {
        // Query one tier easier than what was asked for (Difficulty.oneTierEasier) —
        // "asked for" in the log below still reports their actual choice.
        Difficulty eased = difficulty == null ? null : difficulty.oneTierEasier();
        Problem problem = problems.next(problemId, eased, type, runtime);

        String resolvedLanguage = runtime == null
                ? (language == null || language.isBlank() ? "javascript" : language)
                : runtime.sessionLanguage();

        String id = UUID.randomUUID().toString();
        SessionState state = new SessionState(id, problem, resolvedLanguage);
        state.userId(userId);
        // Started here rather than when the browser asks, and this is the
        // longest head start in the app: the prelude plays for about two
        // seconds before the statement is on screen at all (UI-DESIGN.md
        // §4.8a), which is most of a synthesis paid for by an animation that
        // was going to run anyway.
        state.statementSpeechId(speech.prepareStatement(id, problem.statement()));
        live.put(id, state);

        Session entity = new Session(id, problem.id(), resolvedLanguage, state.startedAt());
        entity.setUserId(userId);
        repository.save(entity);
        log.info("Session {} started: problem={} ({}, {}, asked for {}, user={})",
                id, problem.id(), problem.difficulty(),
                problem.type().label(), difficulty == null ? "any" : difficulty.label(),
                userId == null ? "anonymous" : userId);
        return state;
    }

    public Optional<SessionState> find(String sessionId) {
        return Optional.ofNullable(live.get(sessionId));
    }

    public SessionState require(String sessionId) {
        return find(sessionId).orElseThrow(() -> new UnknownSessionException(sessionId));
    }

    public Collection<SessionState> active() {
        return live.values();
    }

    public Problem problemFor(SessionState state) {
        return state.problem();
    }

    @Transactional
    public void snapshot(SessionState state) {
        repository.findById(state.sessionId()).ifPresent(entity -> {
            entity.setPhase(state.phase());
            entity.setImpatience(state.impatience());
            entity.setFinalCode(mapper.writeValueAsString(state.code()));
            repository.save(entity);
        });
    }

    @Transactional
    public void end(SessionState state) {
        state.phase(Phase.DONE);
        repository.findById(state.sessionId()).ifPresent(entity -> {
            entity.setPhase(Phase.DONE);
            entity.setImpatience(state.impatience());
            entity.setFinalCode(mapper.writeValueAsString(state.code()));
            entity.setEndedAt(Instant.now());
            repository.save(entity);
        });
        live.remove(state.sessionId());
        log.info("Session {} ended after {}", state.sessionId(), state.elapsed());
    }

    public static class UnknownSessionException extends RuntimeException {
        public UnknownSessionException(String sessionId) {
            super("No active session: " + sessionId);
        }
    }
}
