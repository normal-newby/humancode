package com.example.humancode.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.config.OpenAiClientHolder;

import tools.jackson.databind.ObjectMapper;

/**
 * The pool is the difference between a generated interview and the same three
 * bank problems, and its cache is the difference between that and a minute of
 * waiting after every restart. Both are worth a test that does not need a key.
 *
 * <p>The generator here is real but keyless, so {@code generate()} returns empty
 * immediately: no network, no spend, and the restore path is exercised exactly
 * as it runs in production.
 */
class ProblemPoolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("a generated problem survives the JSON round trip")
    void roundTrip() {
        Problem original = problem();

        String json = mapper.writeValueAsString(List.of(original));
        List<Problem> back = mapper.readValue(json, new tools.jackson.core.type.TypeReference<>() {
        });

        assertEquals(1, back.size());
        Problem restored = back.getFirst();
        assertEquals(original.id(), restored.id());
        assertEquals(original.files().size(), restored.files().size());
        // The confidential half has to survive too: it is what the interviewer
        // judges against, and a pooled problem is used exactly like a fresh one.
        assertEquals(original.files().getFirst().referenceContent(), restored.files().getFirst().referenceContent());
        assertEquals(original.rubric(), restored.rubric());
        assertEquals(original.curveballs(), restored.curveballs());
    }

    @Test
    @DisplayName("a pool restored from disk serves the cached problem immediately")
    void restoresAcrossRestarts(@TempDir Path dir) throws IOException {
        Path cache = dir.resolve("problem-pool.json");
        Files.writeString(cache, mapper.writeValueAsString(List.of(problem())));

        ProblemPool pool = pool(cache, 1);
        pool.warmUp();

        Optional<Problem> taken = pool.take(Difficulty.MEDIUM);
        assertTrue(taken.isPresent(), "a cached problem should be served without generating");
        assertEquals("gen-cached", taken.get().id());
    }

    @Test
    @DisplayName("taking a problem rewrites the cache, so a restart does not re-serve it")
    void takeIsPersisted(@TempDir Path dir) throws IOException {
        Path cache = dir.resolve("problem-pool.json");
        Files.writeString(cache, mapper.writeValueAsString(List.of(problem())));

        ProblemPool pool = pool(cache, 1);
        pool.warmUp();
        pool.take(Difficulty.MEDIUM);

        List<Problem> left = mapper.readValue(Files.readString(cache),
                new tools.jackson.core.type.TypeReference<>() {
                });
        assertTrue(left.isEmpty(), "the taken problem should be gone from the cache");
    }

    @Test
    @DisplayName("a difficulty with nothing warm does not get handed another level")
    void neverSubstitutesADifficulty(@TempDir Path dir) throws IOException {
        Path cache = dir.resolve("problem-pool.json");
        // One medium problem cached, and hard is what gets asked for.
        Files.writeString(cache, mapper.writeValueAsString(List.of(problem())));

        ProblemPool pool = pool(cache, 1);
        pool.warmUp();

        assertFalse(pool.take(Difficulty.HARD).isPresent(),
                "a hard request must not be served a medium problem");
        assertTrue(pool.take(Difficulty.MEDIUM).isPresent(), "the medium one is still there");
    }

    @Test
    @DisplayName("a caller with no preference takes whatever is warm")
    void anyDifficultyTakesWhatIsThere(@TempDir Path dir) throws IOException {
        Path cache = dir.resolve("problem-pool.json");
        Files.writeString(cache, mapper.writeValueAsString(List.of(problem())));

        ProblemPool pool = pool(cache, 1);
        pool.warmUp();

        assertTrue(pool.take(null).isPresent());
    }

    @Test
    @DisplayName("a corrupt cache starts cold instead of failing startup")
    void survivesACorruptCache(@TempDir Path dir) throws IOException {
        Path cache = dir.resolve("problem-pool.json");
        Files.writeString(cache, "{ this is not the file you left }");

        ProblemPool pool = pool(cache, 1);
        pool.warmUp();

        assertFalse(pool.take(Difficulty.MEDIUM).isPresent());
    }

    private ProblemPool pool(Path cache, int size) {
        HumancodeProperties props = new HumancodeProperties(
                new HumancodeProperties.Ai("", "gpt-5", "gpt-5-mini", Duration.ofSeconds(30), Duration.ofSeconds(120)),
                new HumancodeProperties.Interview(
                        Duration.ofSeconds(20), Duration.ofSeconds(8), Duration.ofMillis(1500),
                        Duration.ofSeconds(90), 40),
                new HumancodeProperties.Problems("generated", size, Duration.ofSeconds(180),
                        cache.toString()),
                new HumancodeProperties.Speech("", "voice", "eleven_v3", false, Duration.ofSeconds(8), 8));

        // Keyless: generate() returns empty without touching the network.
        ProblemGenerator generator = new ProblemGenerator(new OpenAiClientHolder(null), props);
        return new ProblemPool(generator, mapper, props);
    }

    private Problem problem() {
        return new Problem(
                "gen-cached",
                "Character Counter",
                // Medium on purpose: three tests here turn on the pool refusing
                // to serve one level's problem for another's request.
                "medium",
                List.of("dom", "forms"),
                "Show a live character count under a textarea.",
                List.of(new Problem.ProblemFile(
                        "index.html", "html",
                        "<textarea id=\"box\"></textarea><span id=\"count\"></span>",
                        "<textarea id=\"box\"></textarea><span id=\"count\">0</span>")),
                List.of("Updates the count as you type"),
                List.of("Actually, warn in red past 200 characters."),
                List.of("todo list"));
    }
}
