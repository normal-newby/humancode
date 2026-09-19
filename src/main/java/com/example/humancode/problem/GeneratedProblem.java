package com.example.humancode.problem;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured shape the model returns when generating a problem: either
 * something small to build or something small and deliberately broken to
 * repair. A browser task is an app; a Python task is a logic puzzle.
 *
 * <p>The descriptions here are read by the model alongside the instructions,
 * so they stay runtime-neutral. A word like "app" in this file argues with
 * {@code ProblemGenerator.PYTHON_INSTRUCTIONS}, and the schema is the thing
 * sitting closest to the output.
 *
 * <p>Jackson 2 annotations on purpose — that is what the OpenAI SDK's schema
 * generator reads. See CLAUDE.md §5.
 */
@JsonClassDescription("A small coding interview task, as a set of files to build or repair.")
public record GeneratedProblem(

        @JsonPropertyDescription("Short problem title, e.g. 'Todo List'.")
        String title,

        Difficulty difficulty,

        @JsonPropertyDescription("Two to four lowercase topic tags, e.g. 'dom', 'state', 'parsing'.")
        List<String> tags,

        @JsonPropertyDescription("""
                The task as prose, 2-4 sentences. Plain text, no markdown headings. Describe \
                what the finished code has to do, in the shape the instructions ask for: for \
                a browser task, the small app and how it behaves when used; for a Python \
                task, the rules the data has to be put through and exactly what gets \
                printed.""")
        String statement,

        @JsonPropertyDescription("""
                However many files the task genuinely needs — a browser task often wants an \
                HTML file, a CSS file and a JS file; a Python task is one .py file. At least \
                one file's starterContent must differ meaningfully from its referenceContent; \
                files that need no changes (e.g. a complete HTML shell) may be identical in \
                both.""")
        List<GeneratedFile> files,

        @JsonPropertyDescription("Three to five things a strong answer does, for the interviewer to judge against.")
        List<String> rubric,

        @JsonPropertyDescription("""
                Two to four short, concrete scope-change requests to spring on the candidate \
                mid-task, in the interviewer's voice — for a browser task a small visual or \
                behavioural tweak ('Actually, make the button yellow instead of green.'), for \
                a Python task a change to the rules ('Actually, a tie goes to the later \
                entry.'). Never a new task, never something requiring a different file \
                structure.""")
        List<String> curveballs,

        @JsonPropertyDescription("Three similarly-shaped task ideas.")
        List<String> similarProblems,

        @JsonPropertyDescription("BUILD when the candidate fills a scaffold, BUG_FIX when they repair broken code.")
        ProblemType type) {

    /** Keeps fixtures and older callers working while generated tasks gain a type. */
    public GeneratedProblem(String title, Difficulty difficulty, List<String> tags, String statement,
            List<GeneratedFile> files, List<String> rubric, List<String> curveballs,
            List<String> similarProblems) {
        this(title, difficulty, tags, statement, files, rubric, curveballs, similarProblems, ProblemType.BUILD);
    }

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public record GeneratedFile(
            @JsonPropertyDescription("""
                    Filename with extension, e.g. 'index.html', 'styles.css', 'app.js', or \
                    'shift_planner.py' for a Python task.""")
            String name,

            @JsonPropertyDescription("Monaco language id for this file: 'html', 'css', 'javascript', or 'python'.")
            String language,

            @JsonPropertyDescription("What the candidate sees when the session starts.")
            String starterContent,

            @JsonPropertyDescription("A correct, idiomatic answer for this file. Never shown to the candidate.")
            String referenceContent) {
    }
}
