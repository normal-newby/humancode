package com.example.humancode.interview;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Guards compatibility with the pre-persona-removal SQLite sessions table. */
@SpringBootTest
@ActiveProfiles("dev")
class SessionServicePersistenceTest {

    @Autowired
    private SessionService sessions;

    @Autowired
    private SessionRepository repository;

    @Test
    void startsASessionAgainstTheExistingSchema() {
        SessionState state = sessions.start(null, "javascript");
        try {
            assertNotNull(repository.findById(state.sessionId()).orElse(null));
        } finally {
            repository.deleteById(state.sessionId());
        }
    }
}
