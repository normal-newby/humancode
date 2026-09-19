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
        assertEquals(original.entryPoint(), restored.entryPoint());
        assertEquals(original.tests().size(), restored.tests().size());
        // The confidential half has to survive too: it is what the interviewer
        // judges against, and a pooled problem is used exactly like a fresh one.
        assertEquals(original.referenceSolution(), restored.referenceSolution());
        assertEquals(original.tests().getFirst().expected(), restored.tests().getFirst().expected());
    }

    @Test
    @DisplayName("a pool restored from disk serves the cached problem immediately")
    void restoresAcrossRestarts(@TempDir Path dir) throws IOException {
        Path cache = dir.resolve("problem-pool.json");
        Files.writeString(cache, mapper.writeValueAsString(List.of(problem())));

        ProblemPool pool = pool(cache, 1);
        pool.warmUp();

        Optional<Problem> taken = pool.take();
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
        pool.take();

        List<Problem> left = mapper.readValue(Files.readString(cache),
                new tools.jackson.core.type.TypeReference<>() {
                });
        assertTrue(left.isEmpty(), "the taken problem should be gone from the cache");
    }

    @Test
    @DisplayName("a corrupt cache starts cold instead of failing startup")
    void survivesACorruptCache(@TempDir Path dir) throws IOException {
        Path cache = dir.resolve("problem-pool.json");
        Files.writeString(cache, "{ this is not the file you left }");

        ProblemPool pool = pool(cache, 1);
        pool.warmUp();

        assertFalse(pool.take().isPresent());
    }

    private ProblemPool pool(Path cache, int size) {
        HumancodeProperties props = new HumancodeProperties(
                new HumancodeProperties.Ai("", "gpt-5", "gpt-5-mini", Duration.ofSeconds(30)),
                new HumancodeProperties.Interview(
                        Duration.ofSeconds(20), Duration.ofSeconds(8), Duration.ofMillis(1500)),
                new HumancodeProperties.Problems("generated", size, Duration.ofSeconds(180),
                        cache.toString()));

        // Keyless: generate() returns empty without touching the network.
        ProblemGenerator generator = new ProblemGenerator(new OpenAiClientHolder(null), props, mapper);
        return new ProblemPool(generator, mapper, props);
    }

    private Problem problem() {
        return new Problem(
                "gen-cached",
                "Count Close Pairs",
                "medium",
                List.of("array", "two-pointers"),
                "Count index pairs within K.",
                List.of(),
                "function countClosePairs(nums, k) {}",
                "countClosePairs",
                List.of(new TestCase(List.of(List.of(1, 3, 6, 10), 3), 2)),
                "exact",
                "function countClosePairs(nums, k) { return 2; }",
                "O(n log n)",
                List.of("sorts first"),
                List.of("what if the array is already sorted?"),
                List.of("two sum"));
    }
}
