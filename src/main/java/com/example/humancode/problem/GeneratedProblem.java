package com.example.humancode.problem;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured shape the model returns when generating a problem: a small
 * app to build, in however many files it actually needs.
 *
 * <p>Jackson 2 annotations on purpose — that is what the OpenAI SDK's schema
 * generator reads. See CLAUDE.md §5.
 */
@JsonClassDescription("A small app-building interview task, as a set of files to write.")
public record GeneratedProblem(

        @JsonPropertyDescription("Short problem title, e.g. 'Todo List'.")
        String title,

        Difficulty difficulty,

        @JsonPropertyDescription("Two to four lowercase topic tags, e.g. 'dom', 'forms', 'state'.")
        List<String> tags,

        @JsonPropertyDescription("""
                The task as prose, 2-4 sentences. Plain text, no markdown headings. Describe \
                the small app to build and what it should do when used, not an algorithm.""")
        String statement,

        @JsonPropertyDescription("""
                However many files the task genuinely needs — often an HTML file, a CSS file \
                and a JS file, but fewer if the task is simple enough for one. At least one \
                file's starterContent must differ meaningfully from its referenceContent; \
                files that need no changes (e.g. a complete HTML shell) may be identical in \
                both.""")
        List<GeneratedFile> files,

        @JsonPropertyDescription("Three to five things a strong build does, for the interviewer to judge against.")
        List<String> rubric,

        @JsonPropertyDescription("""
                Two to four short, concrete scope-change requests to spring on the candidate \
                mid-task — a small visual or behavioural tweak to the same app, in the \
                interviewer's voice, e.g. 'Actually, make the button yellow instead of green.' \
                Never a new app, never something requiring a different file structure.""")
        List<String> curveballs,

        @JsonPropertyDescription("Three similarly-shaped small app ideas.")
        List<String> similarProblems) {

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public record GeneratedFile(
            @JsonPropertyDescription("Filename with extension, e.g. 'index.html', 'styles.css', 'app.js'.")
            String name,

            @JsonPropertyDescription("Monaco language id for this file: 'html', 'css', or 'javascript'.")
            String language,

            @JsonPropertyDescription("What the candidate sees when the session starts.")
            String starterContent,

            @JsonPropertyDescription("A correct, idiomatic answer for this file. Never shown to the candidate.")
            String referenceContent) {
    }
}
