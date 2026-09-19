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
        Problem problem = problems.next(problemId, difficulty, type);

        String resolvedLanguage = language == null || language.isBlank() ? "javascript" : language;

        String id = UUID.randomUUID().toString();
        SessionState state = new SessionState(id, problem, resolvedLanguage);
        live.put(id, state);

        repository.save(new Session(id, problem.id(), resolvedLanguage, state.startedAt()));
        log.info("Session {} started: problem={} ({}, {}, asked for {})",
                id, problem.id(), problem.difficulty(),
                problem.type().label(), difficulty == null ? "any" : difficulty.label());
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
