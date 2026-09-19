package com.example.humancode.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

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

/** Asks the model for a fresh small-app-building problem, in however many files it needs. */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProblemGenerator {

    private static final String INSTRUCTIONS = """
            You write small app-building interview tasks for a web development practice tool.
            The candidate is not implementing an algorithm — they are building a tiny,
            self-contained web app (HTML/CSS/JS) that does something visible and interactive.

            Produce ONE self-contained task. Hard requirements:

            - Vanilla HTML, CSS and JavaScript only. No frameworks, no build step, no imports,
              no external resources — everything must run by opening the HTML file directly.
            - Use however many files the task genuinely needs. Most tasks want three: an HTML
              file, a CSS file and a JS file. A simpler task can get by with fewer.
            - `starterContent` for a file is what the candidate opens the session with. At
              least one file's starterContent must differ meaningfully from its
              referenceContent — leave a real, specific gap for the candidate to fill (a
              missing event handler, an incomplete style rule), marked with a
              `// your code here` or `/* your code here */` comment. A file that needs no
              changes (e.g. a complete HTML shell) may have identical starter and reference
              content — do not pad it with a fake gap just to seem incomplete.
            - `referenceContent` for every file must be a genuinely correct, working answer —
              verify by tracing through it mentally before emitting it.
            - The task should be solvable by a competent candidate in 15-25 minutes and produce
              something with visible, interactive behaviour (clicking, typing, toggling), not
              just static markup.
            - Curveballs are small, concrete scope-change requests against the SAME app — a
              visual tweak, an added small feature, a rearrangement. Never a request that would
              need new files or a different structure than what was already built.
            """;

    private static final List<String> SEEDS = List.of(
            "a todo list", "a tip calculator", "a color swatch picker", "a countdown timer",
            "a unit converter", "a quiz with multiple choice questions", "a tic-tac-toe board",
            "a password strength meter", "a simple stopwatch", "a character counter for a textarea",
            "a expandable FAQ accordion", "a star rating widget", "a tabbed content panel",
            "a random quote generator", "a basic drawing canvas with a color picker");

    /**
     * Headroom for reasoning plus several whole files of HTML/CSS/JS as JSON.
     *
     * <p>Same lesson as the old algorithmic generator (CLAUDE.md §6): a truncated
     * response is reported as a JSON parse error, not as truncation, so buy the
     * headroom up front — unused output tokens are not billed.
     */
    private static final long MAX_OUTPUT_TOKENS = 16_000L;

    private static final int MIN_RUBRIC = 3;
    private static final int MIN_CURVEBALLS = 1;

    private final AtomicReference<String> lastSeed = new AtomicReference<>();

    private final OpenAiClientHolder clientHolder;
    private final HumancodeProperties props;

    /** @return empty if generation is unavailable or produced something unusable. */
    public Optional<Problem> generate() {
        Optional<OpenAIClient> client = clientHolder.client();
        if (client.isEmpty()) {
            return Optional.empty();
        }

        String seed = nextSeed();
        String difficulty = ThreadLocalRandom.current().nextInt(3) == 0 ? "medium" : "easy";

        try {
            StructuredResponseCreateParams<GeneratedProblem> params = ResponseCreateParams.builder()
                    .model(props.ai().model())
                    .instructions(INSTRUCTIONS)
                    .input("Write a %s task: build %s. Avoid the most over-used textbook examples."
                            .formatted(difficulty, seed))
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .text(GeneratedProblem.class)
                    .build();

            // Its own deadline, not the client-wide one — see CLAUDE.md §6 on why a short
            // timeout here does not save time, it multiplies the bill by the retry count.
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
                log.warn("Problem generation returned no structured output (status={}, incomplete={})",
                        response.rawResponse().status().map(Object::toString).orElse("unknown"),
                        response.rawResponse().incompleteDetails()
                                .flatMap(details -> details.reason())
                                .map(Object::toString)
                                .orElse("none"));
                return Optional.empty();
            }

            Problem problem = convert(generated.get());
            log.info("Generated problem '{}' ({} files, seed '{}') in {}ms",
                    problem.title(), problem.files().size(), seed, millis);
            return Optional.of(problem);

        } catch (RuntimeException e) {
            String detail = e.toString();
            if (detail.length() > 300) {
                detail = detail.substring(0, 300) + "... [truncated]";
            }
            log.warn("Problem generation failed ({}). A JSON parse error here usually means the"
                    + " response was cut off: check MAX_OUTPUT_TOKENS.", detail);
            return Optional.empty();
        }
    }

    /**
     * A seed, never the one used last.
     *
     * <p>Uniform random over the seed list repeats itself roughly one session in
     * its length, and back-to-back duplicates are the only collision a candidate
     * can actually notice.
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
     * Converts and structurally validates. There is no way to execute arbitrary
     * generated HTML/CSS/JS the way the old generator could run generated test
     * cases, so this is deliberately lighter than that was — it catches
     * malformed output, not a wrong answer. Whether the reference content
     * actually satisfies the rubric is trusted the same way the interviewer's
     * live judgment already is.
     */
    private Problem convert(GeneratedProblem g) {
        if (g.statement() == null || g.statement().isBlank()) {
            throw new IllegalStateException("generated problem has no statement");
        }
        if (g.files() == null || g.files().isEmpty()) {
            throw new IllegalStateException("generated problem has no files");
        }
        if (g.rubric() == null || g.rubric().size() < MIN_RUBRIC) {
            throw new IllegalStateException("generated problem has fewer than " + MIN_RUBRIC + " rubric items");
        }
        if (g.curveballs() == null || g.curveballs().size() < MIN_CURVEBALLS) {
            throw new IllegalStateException("generated problem has no curveballs");
        }

        boolean anyFileHasWork = false;
        List<Problem.ProblemFile> files = new ArrayList<>(g.files().size());
        for (GeneratedProblem.GeneratedFile file : g.files()) {
            if (file.name() == null || file.name().isBlank()) {
                throw new IllegalStateException("generated file has no name");
            }
            if (file.language() == null || file.language().isBlank()) {
                throw new IllegalStateException("file " + file.name() + " has no language");
            }
            if (file.starterContent() == null || file.referenceContent() == null
                    || file.referenceContent().isBlank()) {
                throw new IllegalStateException("file " + file.name() + " is missing starter or reference content");
            }
            if (!file.starterContent().equals(file.referenceContent())) {
                anyFileHasWork = true;
            }
            files.add(new Problem.ProblemFile(file.name(), file.language(),
                    file.starterContent(), file.referenceContent()));
        }
        // The worst possible generated problem is one where every file already
        // matches its answer — the candidate is handed a passing solution to stare at.
        if (!anyFileHasWork) {
            throw new IllegalStateException("no file has a gap between starter and reference content");
        }

        String id = "gen-" + UUID.randomUUID().toString().substring(0, 8);
        return new Problem(
                id,
                g.title(),
                g.difficulty() == null ? "easy" : g.difficulty().name().toLowerCase(Locale.ROOT),
                g.tags() == null ? List.of() : g.tags(),
                g.statement(),
                files,
                g.rubric(),
                g.curveballs(),
                g.similarProblems() == null ? List.of() : g.similarProblems());
    }
}
