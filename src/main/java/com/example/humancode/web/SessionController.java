package com.example.humancode.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.humancode.ai.PersonaLibrary;
import com.example.humancode.ai.PromptAssembler;
import com.example.humancode.interview.InterviewDirector;
import com.example.humancode.interview.Phase;
import com.example.humancode.interview.SessionService;
import com.example.humancode.interview.SessionState;
import com.example.humancode.problem.Problem;
import com.example.humancode.problem.ProblemBank;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService sessions;
    private final InterviewDirector director;
    private final ProblemBank problems;
    private final PersonaLibrary personas;
    private final PromptAssembler prompts;
    private final SseHub sse;

    public SessionController(SessionService sessions, InterviewDirector director, ProblemBank problems,
            PersonaLibrary personas, PromptAssembler prompts, SseHub sse) {
        this.sessions = sessions;
        this.director = director;
        this.problems = problems;
        this.personas = personas;
        this.prompts = prompts;
        this.sse = sse;
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.SessionResponse start(@RequestBody(required = false) Dtos.StartSessionRequest request) {
        Dtos.StartSessionRequest req = request == null
                ? new Dtos.StartSessionRequest(null, null, null)
                : request;
        SessionState state = sessions.start(req.problemId(), req.persona(), req.language());
        return toResponse(state);
    }

    @GetMapping("/sessions/{id}")
    public Dtos.SessionResponse get(@PathVariable String id) {
        return toResponse(sessions.require(id));
    }

    /**
     * The interviewer's channel. Events: {@code connected}, {@code utterance},
     * {@code meter}, {@code note}, {@code phase}.
     */
    @GetMapping(path = "/sessions/{id}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable String id) {
        sessions.require(id);
        return sse.subscribe(id);
    }

    @PostMapping("/sessions/{id}/finish")
    public Dtos.SessionResponse finish(@PathVariable String id) {
        SessionState state = sessions.require(id);
        director.pushPhase(state, Phase.REPORT);
        Dtos.SessionResponse response = toResponse(state);

        // Report card generation lands here next; for now close the session out
        // cleanly so the replay log and final code are persisted.
        sessions.end(state);
        prompts.forget(id);
        sse.close(id);
        return response;
    }

    @GetMapping("/problems")
    public List<Problem> problems() {
        return problems.all().stream().map(Problem::forCandidate).toList();
    }

    @GetMapping("/personas")
    public List<String> personas() {
        return personas.ids();
    }

    private Dtos.SessionResponse toResponse(SessionState state) {
        return new Dtos.SessionResponse(
                state.sessionId(),
                sessions.problemFor(state).forCandidate(),
                state.persona(),
                state.language(),
                state.phase().name(),
                state.impatience(),
                state.transcript(),
                state.notes(),
                true);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(SessionService.UnknownSessionException.class)
    public ResponseEntity<String> unknownSession(SessionService.UnknownSessionException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
