package com.example.humancode.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.config.OpenAiClientHolder;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;

import tools.jackson.databind.ObjectMapper;

/** Asks the model for a fresh problem, complete with runnable test cases. */
@Component
public class ProblemGenerator {

    private static final Logger log = LoggerFactory.getLogger(ProblemGenerator.class);

    private static final String INSTRUCTIONS = """
            You write coding-interview problems for a JavaScript interview practice tool.

            Produce ONE self-contained problem. Hard requirements:

            - JavaScript only. The candidate implements a single top-level function.
            - `starterCode` declares that function, empty, with a JSDoc comment.
            - `referenceSolution` defines the SAME function name and must genuinely pass \
              every test case you write. Verify each case by hand before emitting it.
            - Every `argsJson` is a JSON array of the arguments, in order. A single \
              array argument is therefore double-bracketed: [[1,2,3]].
            - Every `expectedJson` is the exact JSON return value.
            - Use `unordered` ONLY when element order in the returned array is genuinely \
              irrelevant. If the function returns a boolean, number or string, use `exact`.
            - Include edge cases: empty input, a single element, duplicates.
            - Do not use any Node or browser API. Pure computation only.
            - Solvable by a competent candidate in 15-25 minutes.
            """;

    private static final List<String> SEEDS = List.of(
            "arrays and two pointers", "hash maps and counting", "stacks", "strings and parsing",
            "sliding window", "sorting and intervals", "binary search", "matrix traversal",
            "prefix sums", "greedy selection", "linked-list-style logic on arrays", "recursion");

    private final OpenAiClientHolder clientHolder;
    private final HumancodeProperties props;
    private final ObjectMapper mapper;

    public ProblemGenerator(OpenAiClientHolder clientHolder, HumancodeProperties props, ObjectMapper mapper) {
        this.clientHolder = clientHolder;
        this.props = props;
        this.mapper = mapper;
    }

    /** @return empty if generation is unavailable or produced something unusable. */
    public Optional<Problem> generate() {
        Optional<OpenAIClient> client = clientHolder.client();
        if (client.isEmpty()) {
            return Optional.empty();
        }

        String seed = SEEDS.get(ThreadLocalRandom.current().nextInt(SEEDS.size()));
        String difficulty = ThreadLocalRandom.current().nextInt(3) == 0 ? "medium" : "easy";

        try {
            StructuredResponseCreateParams<GeneratedProblem> params = ResponseCreateParams.builder()
                    .model(props.ai().model())
                    .instructions(INSTRUCTIONS)
                    .input("Write a %s problem about %s. Avoid the most over-used textbook examples."
                            .formatted(difficulty, seed))
                    .maxOutputTokens(8000L)
                    .text(GeneratedProblem.class)
                    .build();

            long started = System.nanoTime();
            StructuredResponse<GeneratedProblem> response = client.get().responses().create(params);
            long millis = (System.nanoTime() - started) / 1_000_000;

            Optional<GeneratedProblem> generated = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst();

            if (generated.isEmpty()) {
                log.warn("Problem generation returned no structured output");
                return Optional.empty();
            }

            Problem problem = convert(generated.get());
            log.info("Generated problem '{}' ({} tests, seed '{}') in {}ms",
                    problem.title(), problem.tests().size(), seed, millis);
            return Optional.of(problem);

        } catch (RuntimeException e) {
            log.warn("Problem generation failed ({})", e.toString());
            return Optional.empty();
        }
    }

    /**
     * Converts and structurally validates. The model can still write a subtly
     * wrong test, but a malformed one is caught here rather than in the
     * candidate's face.
     */
    private Problem convert(GeneratedProblem g) {
        if (g.entryPoint() == null || g.entryPoint().isBlank()) {
            throw new IllegalStateException("generated problem has no entry point");
        }
        if (g.tests() == null || g.tests().isEmpty()) {
            throw new IllegalStateException("generated problem has no tests");
        }
        if (g.referenceSolution() == null || !g.referenceSolution().contains(g.entryPoint())) {
            throw new IllegalStateException("reference solution does not define " + g.entryPoint());
        }
        if (g.starterCode() == null || !g.starterCode().contains(g.entryPoint())) {
            throw new IllegalStateException("starter code does not declare " + g.entryPoint());
        }

        List<TestCase> tests = new ArrayList<>(g.tests().size());
        for (GeneratedProblem.GeneratedTest test : g.tests()) {
            List<Object> args = mapper.readValue(test.argsJson(), new tools.jackson.core.type.TypeReference<>() {
            });
            Object expected = mapper.readValue(test.expectedJson(), Object.class);
            tests.add(new TestCase(args, expected));
        }

        List<Problem.Example> examples = g.examples() == null ? List.of()
                : g.examples().stream()
                        .map(e -> new Problem.Example(e.input(), e.output(), e.explanation()))
                        .toList();

        String id = "gen-" + UUID.randomUUID().toString().substring(0, 8);
        return new Problem(
                id,
                g.title(),
                g.difficulty() == null ? "easy" : g.difficulty().name().toLowerCase(Locale.ROOT),
                g.tags() == null ? List.of() : g.tags(),
                g.statement(),
                examples,
                g.starterCode(),
                g.entryPoint(),
                tests,
                g.match() == null ? "exact" : g.match().name().toLowerCase(Locale.ROOT),
                g.referenceSolution(),
                g.optimalComplexity(),
                g.rubric() == null ? List.of() : g.rubric(),
                g.followUps() == null ? List.of() : g.followUps(),
                g.similarProblems() == null ? List.of() : g.similarProblems());
    }
}
