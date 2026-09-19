package com.example.humancode.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

/** Asks the model for a fresh task, either to build or debug. */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProblemGenerator {

    /**
     * What every task has in common, whatever it is written in. The
     * runtime-specific half is {@link #WEB_INSTRUCTIONS} or
     * {@link #PYTHON_INSTRUCTIONS}.
     *
     * <p>They are two blocks rather than one with caveats in it because the
     * shapes genuinely disagree. A browser task is judged on what happens when
     * you click it; a Python task is judged on what a function returns. Asking
     * for both in one breath is how a Python session arrives as a tkinter app.
     */
    private static final String SHARED_INSTRUCTIONS = """
            You write small development interview tasks for a practice tool. The candidate
            either builds something small or finds and repairs bugs in something small, using
            the runtime named in the task prompt.

            Produce ONE self-contained task. Hard requirements:

            - Use only the requested runtime and its standard library. No frameworks, no build
              step, no third-party imports, no network, no files on disk, no external resources.
            - `starterContent` is what the candidate opens the session with. At least one file
              must differ meaningfully from its `referenceContent`.
            - For a BUILD task, leave a real, specific gap for the candidate to fill, marked
              with a `your code here` comment. A file that needs no changes may be identical in
              both versions.
            - For a BUG_FIX task, give the candidate complete-looking, runnable code with two
              to four intentional behavioural bugs. The statement describes the intended
              behaviour but does not name the broken lines. Do not use `your code here` markers
              in this type. The reference fixes the bugs without changing the scope.
            - `referenceContent` for every file must be a genuinely correct, working answer -
              verify by tracing through it mentally before emitting it.
            - How big the task should be is set by the difficulty calibration in the request,
              which is the only place a time budget is named - follow it.
            - Curveballs are small, concrete scope-change requests against the SAME task. Never
              one that would need new files or a different structure than what is already there.
            """;

    private static final String WEB_INSTRUCTIONS = """

            This one is a browser task. Vanilla HTML, CSS and JavaScript only, and no Python.

            - Use however many files the task genuinely needs. Most want three: an HTML file, a
              CSS file and a JS file. A simpler task can get by with fewer.
            - It must produce something with visible, interactive behaviour - clicking, typing,
              toggling - not just static markup.
            - A curveball here is a visual tweak, a small added feature, or a rearrangement.
            """;

    private static final String PYTHON_INSTRUCTIONS = """

            This one is a Python logic puzzle, and it is NOT an application. Python 3 and the
            standard library only, and no HTML, CSS or JavaScript.

            - No interface of any kind. No tkinter, curses, pygame or Qt, no web server, no menu
              loop, no `input()`, no `sys.argv`, no reading a file. Nothing is clicked and
              nothing is typed at it. This outranks any instinct to make the task feel like an
              app: there is no window for the candidate to look at, so a task that opens one is
              a failed generation.
            - Exactly one file, a `.py` named after the puzzle, with language 'python'.
            - Always the same shape. The sample data is a module-level constant at the top of
              the file, the candidate writes one to three pure functions that turn it into an
              answer under a set of rules, and `main()` prints that answer. The same input must
              give the same output every run, so nothing reads a clock or a random number.
            - The difficulty lives in the rules, never in the plumbing: how rules take
              precedence over each other, how ties break, how things group and order, which edge
              case the obvious loop gets wrong. State the rules exactly, and state exactly what
              the printed output looks like, so there is one right answer to judge against.
            - Put malformed or edge-case entries in the sample data on purpose, and say in the
              statement what should happen to them, unless the calibration rules them out.
            - Count the rules before you write the statement. If you cannot state all of them in
              four sentences a candidate can hold in their head, the puzzle is too big: cut one
              rather than writing a longer statement. The calibration below says how many the
              level buys, and that is a ceiling, not a target.
            - Do not restate a famous exercise - no FizzBuzz, two-sum, fibonacci, anagrams,
              balanced brackets or roman numerals. Dress the rules in a small real situation and
              keep the work pure logic.
            - A curveball here is a rule change to the same puzzle, such as a tie going the other
              way or one more condition, and never a request for an interface.
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
     * Situations with rules in them, never a noun that suggests a screen.
     *
     * <p>"A todo list" handed to a Python generation gets you a todo list with a
     * window around it, whatever the instructions say, because the seed is the
     * most concrete thing in the prompt.
     */
    private static final List<String> PYTHON_SEEDS = List.of(
            "assigning on-call shifts under a handful of rules",
            "resolving discount rules that overlap on one order",
            "seating a party across fixed tables",
            "deciding which log lines a rate limiter drops",
            "reconciling two inventory counts that disagree",
            "picking a winner from ranked ballots",
            "collapsing overlapping calendar bookings",
            "routing support tickets by priority rules",
            "settling who owes whom after a shared trip",
            "expanding a compact seat-range notation",
            "choosing which cache entries to evict",
            "grading an answer sheet that allows partial credit",
            "merging config layers that override each other",
            "scoring a tournament ladder with tie-breaks",
            "working out delivery windows across time zones");

    private static final List<String> PYTHON_BUG_FIX_SEEDS = List.of(
            "a leaderboard that breaks ties the wrong way",
            "a date-range merger that loses a range",
            "an invoice totaller that rounds in the wrong place",
            "a log parser that quietly skips valid lines",
            "a permission resolver that grants too much",
            "a retry scheduler that backs off wrongly");

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
        String seed = nextSeed(type, runtime);
        Difficulty level = difficulty != null ? difficulty : randomDifficulty();

        try {
            StructuredResponseCreateParams<GeneratedProblem> params = ResponseCreateParams.builder()
                    .model(props.ai().model())
                    .instructions(instructions(runtime))
                    .input(("Write a %s %s %s about %s. Set type to %s. Avoid the most over-used"
                            + " textbook examples. %s")
                            .formatted(level.label(), type == ProblemType.BUG_FIX ? "bug-fix" : "build",
                                    runtime == ProblemRuntime.PYTHON ? "Python logic puzzle" : "browser task",
                                    seed, type.name(), calibration(level, runtime)))
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
            if (runtime == ProblemRuntime.PYTHON) {
                requirePureLogic(problem);
            }
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

    private static String instructions(ProblemRuntime runtime) {
        return SHARED_INSTRUCTIONS
                + (runtime == ProblemRuntime.PYTHON ? PYTHON_INSTRUCTIONS : WEB_INSTRUCTIONS);
    }

    /** Anything that opens a window or waits on a human. */
    private static final Pattern PYTHON_INTERFACE = Pattern.compile(
            "\\b(tkinter|Tkinter|curses|pygame|PyQt\\d?|PySide\\d?|kivy)\\b|\\binput\\s*\\(");

    /**
     * Rejects a Python task that turned out to be an app after all.
     *
     * <p>Worth a wasted call, unlike most extra validation (see
     * {@link #convert}): there is no runner and no preview for Python
     * (CLAUDE.md §6), so a generated tkinter app is a window the candidate can
     * never see and the interviewer can only guess at. The pattern is narrow
     * enough that a real logic puzzle cannot trip it, and the fallback is a
     * bank Python problem, which is the right shape by construction.
     */
    static void requirePureLogic(Problem problem) {
        for (Problem.ProblemFile file : problem.files()) {
            if (!"python".equals(file.language()) && !file.name().endsWith(".py")) {
                continue;
            }
            var match = PYTHON_INTERFACE.matcher(file.starterContent() + "\n" + file.referenceContent());
            if (match.find()) {
                throw new IllegalStateException("generated python problem builds an interface ('"
                        + match.group().trim() + "' in " + file.name() + ")");
            }
        }
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
    private static String calibration(Difficulty level, ProblemRuntime runtime) {
        return runtime == ProblemRuntime.PYTHON ? pythonCalibration(level) : webCalibration(level);
    }

    private static String webCalibration(Difficulty level) {
        return switch (level) {
            case VERY_EASY -> "Very easy means one screen, one button and one line of state,"
                    + " finishable in 3 to 5 minutes. Exactly one gap, in one file, filling one"
                    + " function body that is three or four lines long — the HTML and CSS are"
                    + " complete and the candidate never opens them. Do not add a second"
                    + " requirement to make it feel more like an interview question. It is not"
                    + " one, it is the shortest possible thing that still reacts to a click.";
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
     * The same idea for a puzzle: the level buys how many rules have to agree,
     * not how much typing there is.
     */
    private static String pythonCalibration(Difficulty level) {
        return switch (level) {
            case VERY_EASY -> "Very easy means ONE rule applied to one short list, finishable in 3"
                    + " to 5 minutes: filter it, total it, or pick the winner. One function body"
                    + " is the only gap, three or four lines long. No tie-breaks, no precedence,"
                    + " no grouping, no malformed entries, and no second function. Say the rule in"
                    + " one sentence. If the statement needs a second sentence to explain the"
                    + " rules, the puzzle is too big for this level.";
            case EASY -> "Easy means one set of rules applied to one list, with no interaction"
                    + " between the rules, solvable in 10 to 15 minutes. The gap left in the"
                    + " starter is one function body.";
            case MEDIUM -> "Medium means two or three rules that have to agree with each other,"
                    + " plus an ordering or a tie-break, solvable in 20 to 30 minutes. Leave two"
                    + " function bodies open, and put a case in the sample data that the obvious"
                    + " loop gets wrong.";
            case HARD -> "Hard means the candidate has to decide how to represent the data before"
                    + " writing anything, and the rules compose so that applying them in the wrong"
                    + " order gives a plausible wrong answer - 30 to 45 minutes. Still one file,"
                    + " still readable in one sitting, but the modelling is the point.";
        };
    }

    /**
     * A seed, never the one used last.
     *
     * <p>Uniform random over the seed list repeats itself roughly one session in
     * its length, and back-to-back duplicates are the only collision a candidate
     * can actually notice.
     */
    private String nextSeed(ProblemType type, ProblemRuntime runtime) {
        List<String> seeds = runtime == ProblemRuntime.PYTHON
                ? (type == ProblemType.BUG_FIX ? PYTHON_BUG_FIX_SEEDS : PYTHON_SEEDS)
                : (type == ProblemType.BUG_FIX ? BUG_FIX_SEEDS : SEEDS);
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
