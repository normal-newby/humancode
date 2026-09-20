package com.example.humancode.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.humancode.interview.SessionService;
import com.example.humancode.interview.SessionState;
import com.example.humancode.user.Handles;
import com.example.humancode.user.UserService;

/**
 * Shared handlers for every {@code /api} controller.
 *
 * <p>This exists because {@link SessionService.UnknownSessionException} is not
 * only thrown by {@link SessionController}: the last telemetry batch races the
 * {@code /finish} call that removed the session, so a perfectly healthy session
 * ends with one late {@code POST /telemetry} against an id that is gone. Handled
 * only on the one controller, that surfaced as a 500 and a stack trace in the
 * log while the UI carried on fine.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(SessionService.UnknownSessionException.class)
    ResponseEntity<String> unknownSession(SessionService.UnknownSessionException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    /**
     * The client disables the hint button once {@code hintsRemaining} hits
     * zero, so this is a defensive backstop — a double click racing the
     * response, or a second tab on the same session — not the normal path.
     */
    @ExceptionHandler(SessionState.HintsExhaustedException.class)
    ResponseEntity<String> hintsExhausted(SessionState.HintsExhaustedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("No hints left this session");
    }

    /**
     * The three handle failures, and all three bodies are written to be shown
     * to the candidate verbatim — the start screen prints whatever comes back
     * under their handle line, in the app's own lowercase, so a stack-trace
     * shaped message would land on screen.
     */
    @ExceptionHandler(Handles.InvalidHandleException.class)
    ResponseEntity<String> invalidHandle(Handles.InvalidHandleException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(UserService.HandleTakenException.class)
    ResponseEntity<String> handleTaken(UserService.HandleTakenException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("that handle is taken");
    }

    @ExceptionHandler(UserService.UnauthorizedException.class)
    ResponseEntity<String> notYours(UserService.UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("that handle is not yours");
    }

    @ExceptionHandler(UserService.UnknownUserException.class)
    ResponseEntity<String> unknownUser(UserService.UnknownUserException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("nobody by that name");
    }
}
