package com.example.humancode.problem;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.openai.models.responses.ResponseCreateParams;

/**
 * The SDK derives a strict JSON schema from {@link GeneratedProblem} and
 * validates it locally when the params are built — no network, no API key.
 *
 * <p>Worth guarding: production generates every problem through this shape, so
 * a schema break would take the whole app down, and only in the environment
 * where nobody is watching a terminal.
 */
class GeneratedProblemSchemaTest {

    @Test
    @DisplayName("GeneratedProblem produces a schema the Responses API will accept")
    void generatedProblemSchemaIsValid() {
        assertDoesNotThrow(() -> ResponseCreateParams.builder()
                .model("gpt-5")
                .instructions("You write coding-interview problems.")
                .input("Write an easy problem about hash maps.")
                .maxOutputTokens(8000L)
                .text(GeneratedProblem.class)
                .build());
    }
}
