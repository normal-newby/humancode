package com.example.humancode.problem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.config.OpenAiClientHolder;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import tools.jackson.databind.ObjectMapper;

/** Asks the model for a fresh problem, complete with runnable test cases. */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProblemGenerator {

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

    /**
     * Headroom for reasoning plus a whole problem as JSON.
     *
     * <p>8000 was not enough: a mid-sized problem with eight tests, a reference
     * solution and a rubric ran out partway through a later field, and the SDK
     * threw {@code OpenAIInvalidDataException} on the truncated JSON. That
     * failure costs a full 90-second call and produces nothing, so buy the
     * headroom — unused output tokens are not billed.
     */
    private static final long MAX_OUTPUT_TOKENS = 16_000L;

    private static final Pattern JS_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern RETURN_STATEMENT = Pattern.compile("\\breturn\\b");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    /** Two sessions in a row on "stacks" is not a random problem source. */
    private static final int MIN_TESTS = 3;

    private final AtomicReference<String> lastSeed = new AtomicReference<>();

    private final OpenAiClientHolder clientHolder;
    private final HumancodeProperties props;
    private final ObjectMapper mapper;

    /**
     * @param difficulty what to ask for; null picks one the way it used to
     * @return empty if generation is unavailable or produced something unusable
     */
    public Optional<Problem> generate(Difficulty difficulty) {
        Optional<OpenAIClient> client = clientHolder.client();
        if (client.isEmpty()) {
            return Optional.empty();
        }

        String seed = nextSeed();
        Difficulty level = difficulty != null ? difficulty : randomDifficulty();

        try {
            StructuredResponseCreateParams<GeneratedProblem> params = ResponseCreateParams.builder()
                    .model(props.ai().model())
                    .instructions(INSTRUCTIONS)
                    .input(("Write a %s problem about %s. Avoid the most over-used textbook examples."
                            + " %s")
                            .formatted(level.label(), seed, calibration(level)))
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .text(GeneratedProblem.class)
                    .build();

            // Its own deadline, not the client-wide one. A problem takes the
            // model a minute and a half to write; at the 30s default the SDK
            // does not fail, it retries, so every problem is quietly paid for
            // two or three times over.
            RequestOptions options = RequestOptions.builder()
                    .timeout(props.problems().generationTimeout())
                    .build();

            long started = System.nanoTime();
            StructuredResponse<GeneratedProblem> response = client.get().responses().create(params, options);
            long millis = (System.nanoTime() - started) / 1_000_000;

            Optional<GeneratedProblem> generated = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst();

            if (generated.isEmpty()) {
                // Same trap as the quip path: a truncated reasoning response
                // carries no message and looks identical to a refusal.
                log.warn("Problem generation returned no structured output (status={}, incomplete={})",
                        response.rawResponse().status().map(Object::toString).orElse("unknown"),
                        response.rawResponse().incompleteDetails()
                                .flatMap(details -> details.reason())
                                .map(Object::toString)
                                .orElse("none"));
                return Optional.empty();
            }

            Problem problem = convert(generated.get(), level);
            log.info("Generated {} problem '{}' ({} tests, seed '{}') in {}ms",
                    problem.difficulty(), problem.title(), problem.tests().size(), seed, millis);
            return Optional.of(problem);

        } catch (RuntimeException e) {
            // The SDK reports a truncated response as a JSON parse failure and
            // pastes the whole partial body into the message, which is both
            // enormous and misleading. Name the likely cause and trim it.
            String detail = e.toString();
            if (detail.length() > 300) {
                detail = detail.substring(0, 300) + "... [truncated]";
            }
            // The hint only applies to a parse failure. Printing it on a
            // validation rejection sends the next reader hunting a token limit
            // that has nothing to do with it.
            boolean looksTruncated = e instanceof IllegalStateException
                    ? false
                    : detail.contains("parsing JSON") || detail.contains("InvalidData");
            log.warn("Problem generation failed ({}){}", detail, looksTruncated
                    ? ". A JSON parse error usually means the response was cut off: check"
                            + " MAX_OUTPUT_TOKENS."
                    : "");
            return Optional.empty();
        }
    }

    /**
     * Code with its comments removed, for checks that must not read a JSDoc
     * tag as source. Good enough for this: a comment marker inside a string
     * literal would confuse it, and a generated starter has no string literals.
     */
    private static String stripComments(String code) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(code).replaceAll(" ")).replaceAll(" ");
    }

    private static Difficulty randomDifficulty() {
        return ThreadLocalRandom.current().nextInt(3) == 0 ? Difficulty.MEDIUM : Difficulty.EASY;
    }

    /**
     * Anchors the level to something concrete.
     *
     * <p>"Write a hard problem" on its own gets you an easy problem with an
     * intimidating statement. The model needs to be told what the word buys in
     * minutes and in technique.
     */
    private static String calibration(Difficulty level) {
        return switch (level) {
            case EASY -> "Easy means one idea and one data structure, solvable in 10 to 15 minutes by"
                    + " a competent candidate. No multi-step algorithm.";
            case MEDIUM -> "Medium means two ideas composed, or one idea with a non-obvious edge case,"
                    + " solvable in 20 to 30 minutes. The naive solution should be obvious and wrong"
                    + " on complexity.";
            case HARD -> "Hard means the candidate must find a non-obvious insight before writing any"
                    + " code, and the brute force is clearly infeasible. 30 to 45 minutes. Still one"
                    + " function, still pure computation, and the reference solution must stay short"
                    + " enough to verify by hand.";
        };
    }

    /**
     * A seed, never the one used last.
     *
     * <p>Uniform random over twelve seeds repeats itself roughly one session in
     * twelve, and back-to-back duplicates are the only collision a candidate can
     * actually notice.
     */
    private String nextSeed() {
        String previous = lastSeed.get();
        String seed;
        do {
            seed = SEEDS.get(ThreadLocalRandom.current().nextInt(SEEDS.size()));
        } while (seed.equals(previous) && SEEDS.size() > 1);
        lastSeed.set(seed);
        return seed;
    }

    /**
     * Converts and structurally validates. The model can still write a subtly
     * wrong test, but a malformed one is caught here rather than in the
     * candidate's face.
     */
    // Package-private: the validation below is the only thing standing between
    // a malformed generation and a candidate's screen, so it is tested directly.
    Problem convert(GeneratedProblem g, Difficulty requested) {
        if (g.entryPoint() == null || !JS_IDENTIFIER.matcher(g.entryPoint()).matches()) {
            throw new IllegalStateException("entry point is not a usable function name: " + g.entryPoint());
        }
        if (g.tests() == null || g.tests().size() < MIN_TESTS) {
            throw new IllegalStateException("generated problem has fewer than " + MIN_TESTS + " tests");
        }
        if (g.referenceSolution() == null || !g.referenceSolution().contains(g.entryPoint())) {
            throw new IllegalStateException("reference solution does not define " + g.entryPoint());
        }
        if (g.starterCode() == null || !g.starterCode().contains(g.entryPoint())) {
            throw new IllegalStateException("starter code does not declare " + g.entryPoint());
        }
        // The worst possible generated problem is one whose starter code is the
        // answer: it fails no other structural check, and the candidate is
        // handed a passing solution to stare at. An empty body cannot return
        // anything, so that is the thing to look for — but only outside
        // comments, because every starter carries a JSDoc `@return` tag.
        if (RETURN_STATEMENT.matcher(stripComments(g.starterCode())).find()) {
            throw new IllegalStateException("starter code already contains a return statement");
        }
        if (g.statement() == null || g.statement().isBlank()) {
            throw new IllegalStateException("generated problem has no statement");
        }

        List<TestCase> tests = new ArrayList<>(g.tests().size());
        Set<String> seenArgs = new HashSet<>();
        for (GeneratedProblem.GeneratedTest test : g.tests()) {
            List<Object> args = mapper.readValue(test.argsJson(), new tools.jackson.core.type.TypeReference<>() {
            });
            Object expected = mapper.readValue(test.expectedJson(), Object.class);
            // A duplicated case is a test the model thought it had written and
            // did not: it inflates the count while covering nothing.
            if (!seenArgs.add(test.argsJson())) {
                throw new IllegalStateException("duplicate test case: " + test.argsJson());
            }
            tests.add(new TestCase(args, expected));
        }

        String id = "gen-" + UUID.randomUUID().toString().substring(0, 8);
        return new Problem(
                id,
                g.title(),
                // What was asked for wins over what the model labelled it. The
                // candidate chose this; a model that writes an easy problem and
                // calls it hard must not also get to relabel the session.
                requested.label(),
                g.tags() == null ? List.of() : g.tags(),
                g.statement(),
                // Generated problems carry no worked examples, by design.
                List.of(),
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
