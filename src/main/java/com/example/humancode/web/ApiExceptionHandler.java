package com.example.humancode.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.humancode.interview.SessionService;
import com.example.humancode.interview.SessionState;

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
}
