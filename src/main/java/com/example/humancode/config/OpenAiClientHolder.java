package com.example.humancode.config;

import java.util.Optional;

import com.openai.client.OpenAIClient;

import lombok.RequiredArgsConstructor;

/**
 * Wraps the SDK client so the application starts cleanly with no API key set.
 *
 * <p>{@code OpenAIOkHttpClient.fromEnv()} throws when {@code OPENAI_API_KEY} is
 * absent. Letting that happen at startup would mean nobody can run the app
 * until the key lands, so instead we hold a possibly-absent client and let
 * callers degrade — see {@code Interviewer}, which falls back to canned lines.
 */
@RequiredArgsConstructor
public final class OpenAiClientHolder {

    private final OpenAIClient client;

    public boolean isConfigured() {
        return client != null;
    }

    public Optional<OpenAIClient> client() {
        return Optional.ofNullable(client);
    }
}
