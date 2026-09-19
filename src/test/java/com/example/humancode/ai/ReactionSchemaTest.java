package com.example.humancode.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.openai.models.responses.ResponseCreateParams;

/**
 * The SDK derives a strict JSON schema from {@link Reaction} and validates it
 * locally when the params are built — no network, no API key. That makes this
 * the cheapest possible guard against the structured-output contract breaking,
 * which otherwise only shows up as a 400 in the middle of a live demo.
 */
class ReactionSchemaTest {

    @Test
    @DisplayName("Reaction produces a schema the Responses API will accept")
    void reactionSchemaIsValid() {
        assertDoesNotThrow(() -> ResponseCreateParams.builder()
                .model("gpt-5-mini")
                .instructions("You are conducting an interview.")
                .input("The candidate has not typed for 30 seconds.")
                .maxOutputTokens(2000L)
                .text(Reaction.class)
                .build());
    }
}
