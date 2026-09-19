package com.example.humancode.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.humancode.ai.PromptAssembler;
import com.example.humancode.interview.InterviewDirector;
import com.example.humancode.interview.Phase;
import com.example.humancode.interview.SessionService;
import com.example.humancode.interview.SessionState;
import com.example.humancode.problem.Problem;
import com.example.humancode.problem.Difficulty;
import com.example.humancode.problem.ProblemBank;
import com.example.humancode.report.ReportCard;
import com.example.humancode.report.ReportCardGenerator;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService sessions;
    private final InterviewDirector director;
    private final ProblemBank problems;
    private final PromptAssembler prompts;
    private final ReportCardGenerator reportCardGenerator;
    private final SseHub sse;

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.SessionResponse start(@RequestBody(required = false) Dtos.StartSessionRequest request) {
        Dtos.StartSessionRequest req = request == null
                ? new Dtos.StartSessionRequest(null, null, null)
                : request;
        SessionState state = sessions.start(req.problemId(), req.language(),
                Difficulty.parse(req.difficulty()).orElse(null));
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

        // Generated while the session is still live in memory — a generated
        // problem exists only for the life of its session (CLAUDE.md §6), so
        // similarProblems has to be read here or it is gone after sessions.end().
        ReportCard report = reportCardGenerator.generate(state, sessions.problemFor(state));
        Dtos.SessionResponse response = toResponse(state, report);

        sessions.end(state);
        prompts.forget(id);
        sse.close(id);
        return response;
    }

    @GetMapping("/problems")
    public List<Problem> problems() {
        return problems.all().stream().map(Problem::forCandidate).toList();
    }

    private Dtos.SessionResponse toResponse(SessionState state) {
        return toResponse(state, null);
    }

    private Dtos.SessionResponse toResponse(SessionState state, ReportCard report) {
        return new Dtos.SessionResponse(
                state.sessionId(),
                sessions.problemFor(state).forCandidate(),
                state.language(),
                state.phase().name(),
                state.impatience(),
                state.transcript(),
                state.notes(),
                true,
                report);
    }
}
