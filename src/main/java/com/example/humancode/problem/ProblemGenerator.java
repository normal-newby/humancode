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

/** Asks the model for a fresh web task, either to build or debug. */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProblemGenerator {

    private static final String INSTRUCTIONS = """
            You write small development interview tasks for a practice tool. The candidate is
            not implementing an algorithm. They either build a small app or find and repair
            bugs in one, using the runtime requested in the task prompt.

            Produce ONE self-contained task. Hard requirements:

            - Use only the requested runtime and its standard capabilities. No frameworks,
              build step, imports from third parties or external resources.
            - Use however many files the task genuinely needs. Most tasks want three: an HTML
              file, a CSS file and a JS file. A simpler task can get by with fewer.
            - `starterContent` is what the candidate opens the session with. At least one file
              must differ meaningfully from its `referenceContent`.
            - For a BUILD task, leave a real, specific gap for the candidate to fill, marked
              with `// your code here` or `/* your code here */`. A file that needs no changes
              may be identical in both versions.
            - For a BUG_FIX task, give the candidate a complete-looking, runnable app with two
              to four intentional behavioural bugs. The statement describes the intended
              behaviour but does not name the broken lines. Do not use `your code here` markers
              in this type. The reference fixes the bugs without changing the app's scope.
            - `referenceContent` for every file must be a genuinely correct, working answer —
              verify by tracing through it mentally before emitting it.
            - The task must produce something with visible, interactive behaviour (clicking,
              typing, toggling), not just static markup. How big it should be is set by the
              difficulty calibration in the request, which is the only place a time budget
              is named — follow it.
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

    private static final List<String> BUG_FIX_SEEDS = List.of(
            "a quantity stepper", "a live character counter", "a tabbed settings panel",
            "a shopping-cart summary", "a password visibility toggle", "a filterable list");

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

    /**
     * @param difficulty what to ask for; null picks one the way it used to
     * @return empty if generation is unavailable or produced something unusable
     */
    public Optional<Problem> generate(Difficulty difficulty) {
        return generate(difficulty, null);
    }

    /** Generates the requested task shape when a candidate selected one. */
    public Optional<Problem> generate(Difficulty difficulty, ProblemType requestedType) {
        return generate(difficulty, requestedType, null);
    }

    /** Generates a browser or Python task according to the candidate's selected runtime. */
    public Optional<Problem> generate(Difficulty difficulty, ProblemType requestedType,
            ProblemRuntime requestedRuntime) {
        Optional<OpenAIClient> client = clientHolder.client();
        if (client.isEmpty()) {
            return Optional.empty();
        }

        ProblemType type = requestedType == null ? nextType() : requestedType;
        ProblemRuntime runtime = requestedRuntime == null ? ProblemRuntime.WEB : requestedRuntime;
        String seed = nextSeed(type);
        Difficulty level = difficulty != null ? difficulty : randomDifficulty();

        try {
            StructuredResponseCreateParams<GeneratedProblem> params = ResponseCreateParams.builder()
                    .model(props.ai().model())
                    .instructions(INSTRUCTIONS)
                    .input(("Write a %s %s %s task about %s. Set type to %s. %s Avoid the most over-used"
                            + " textbook examples. %s")
                            .formatted(level.label(), type == ProblemType.BUG_FIX ? "bug-fix" : "build",
                                    runtime.label(), seed, type.name(), runtimeInstructions(runtime),
                                    calibration(level)))
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

            Problem problem = convert(generated.get(), level, type);
            log.info("Generated {} {} problem '{}' ({} files, seed '{}') in {}ms",
                    problem.difficulty(), problem.type(), problem.title(), problem.files().size(), seed, millis);
            return Optional.of(problem);

        } catch (RuntimeException e) {
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

    private static Difficulty randomDifficulty() {
        return ThreadLocalRandom.current().nextInt(3) == 0 ? Difficulty.MEDIUM : Difficulty.EASY;
    }

    /** Debugging should be common enough to appear, without replacing build work. */
    private static ProblemType nextType() {
        return ThreadLocalRandom.current().nextInt(3) == 0 ? ProblemType.BUG_FIX : ProblemType.BUILD;
    }

    private static String runtimeInstructions(ProblemRuntime runtime) {
        return runtime == ProblemRuntime.PYTHON
                ? "Use Python 3 and the standard library only. Supply .py files with Monaco language 'python';"
                        + " do not include HTML, CSS or JavaScript."
                : "Use vanilla HTML, CSS and JavaScript only; do not include Python.";
    }

    /**
     * Anchors the level to something concrete.
     *
     * <p>"Write a hard task" on its own gets you an easy task with an
     * intimidating statement. The model needs to be told what the word buys,
     * and for a build-a-small-app task that is minutes, moving parts and how
     * much state the candidate has to keep straight — not complexity classes.
     * The instructions deliberately leave the time budget to this method; two
     * places naming a duration is how you get a model that splits the
     * difference and ignores both.
     */
    private static String calibration(Difficulty level) {
        return switch (level) {
            case EASY -> "Easy means one screen, one interaction and no state beyond what is on it,"
                    + " solvable in 10 to 15 minutes by a competent candidate. The gap left in the"
                    + " starter should be one handler or one rule.";
            case MEDIUM -> "Medium means two or three interactions that have to agree with each"
                    + " other, and state that outlives a single click, solvable in 20 to 30"
                    + " minutes. Leave gaps in more than one file.";
            case HARD -> "Hard means the candidate must decide how to model the state before"
                    + " writing anything, and a naive per-element approach falls apart once there"
                    + " are several — 30 to 45 minutes. Still vanilla HTML, CSS and JS, still small"
                    + " enough to read in one sitting, but the wiring is the point.";
        };
    }

    /**
     * A seed, never the one used last.
     *
     * <p>Uniform random over the seed list repeats itself roughly one session in
     * its length, and back-to-back duplicates are the only collision a candidate
     * can actually notice.
     */
    private String nextSeed(ProblemType type) {
        List<String> seeds = type == ProblemType.BUG_FIX ? BUG_FIX_SEEDS : SEEDS;
        String previous = lastSeed.get();
        String seed;
        do {
            seed = seeds.get(ThreadLocalRandom.current().nextInt(seeds.size()));
        } while (seed.equals(previous) && seeds.size() > 1);
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
    // Package-private: the validation below is the only thing standing between
    // a malformed generation and a candidate's screen, so it is tested directly.
    Problem convert(GeneratedProblem g, Difficulty requested) {
        return convert(g, requested, g.type() == null ? ProblemType.BUILD : g.type());
    }

    Problem convert(GeneratedProblem g, Difficulty requested, ProblemType requestedType) {
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
                // What was asked for wins over what the model labelled it. The
                // candidate chose this; a model that writes an easy problem and
                // calls it hard must not also get to relabel the session.
                requested.label(),
                g.tags() == null ? List.of() : g.tags(),
                g.statement(),
                files,
                g.rubric(),
                g.curveballs(),
                g.similarProblems() == null ? List.of() : g.similarProblems(),
                requestedType);
    }
}
