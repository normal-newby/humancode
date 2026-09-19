package com.example.humancode.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The diff feeds every reaction the interviewer makes. A wrong one does not
 * throw, it just makes the interviewer talk about code the candidate never
 * wrote, which is the hardest kind of bug to notice in a demo.
 */
class CodeDiffTest {

    @Test
    @DisplayName("identical buffers produce nothing at all")
    void unchanged() {
        String code = "function twoSum(nums) {\n  return [];\n}";
        assertEquals("", CodeDiff.unified(code, code));
    }

    @Test
    @DisplayName("trailing whitespace is not a change")
    void ignoresTrailingWhitespace() {
        assertEquals("", CodeDiff.unified("const a = 1;", "const a = 1;   "));
    }

    @Test
    @DisplayName("an added line is reported with its new line number")
    void addition() {
        String before = "function twoSum(nums) {\n}";
        String after = "function twoSum(nums) {\n  const seen = new Map();\n}";

        String diff = CodeDiff.unified(before, after);

        // Leading indentation is preserved, so the marker is followed by the
        // line exactly as it sits in the editor.
        assertTrue(diff.contains("+ 2    const seen = new Map();"), diff);
        assertFalse(diff.contains("-"), diff);
    }

    @Test
    @DisplayName("a removed line is reported with its old line number")
    void removal() {
        String before = "const a = 1;\nconst b = 2;\nconst c = 3;";
        String after = "const a = 1;\nconst c = 3;";

        String diff = CodeDiff.unified(before, after);

        assertTrue(diff.contains("- 2  const b = 2;"), diff);
        assertFalse(diff.contains("+"), diff);
    }

    @Test
    @DisplayName("a rewrite shows both sides, which is the reaction-worthy case")
    void rewrite() {
        String before = "for (let i = 0; i < nums.length; i++) {";
        String after = "const seen = new Map();";

        String diff = CodeDiff.unified(before, after);

        assertTrue(diff.contains("- 1  for (let i = 0; i < nums.length; i++) {"), diff);
        assertTrue(diff.contains("+ 1  const seen = new Map();"), diff);
    }

    @Test
    @DisplayName("unchanged lines around an edit are not reported")
    void onlyTheChange() {
        String before = "line one\nline two\nline three";
        String after = "line one\nline TWO\nline three";

        String diff = CodeDiff.unified(before, after);

        assertFalse(diff.contains("line one"), diff);
        assertFalse(diff.contains("line three"), diff);
        assertTrue(diff.contains("line two"), diff);
        assertTrue(diff.contains("line TWO"), diff);
    }

    @Test
    @DisplayName("a paste of a novel is truncated rather than billed for")
    void capsHugeDiffs() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            huge.append("const x").append(i).append(" = ").append(i).append(";\n");
        }

        String diff = CodeDiff.unified("", huge.toString());

        assertTrue(diff.lines().count() <= 41, "diff was " + diff.lines().count() + " lines");
        assertTrue(diff.contains("more changed lines"), diff);
    }

    @Test
    @DisplayName("an empty editor against starter code is a clean removal, not a crash")
    void emptyBuffers() {
        assertEquals("", CodeDiff.unified("", ""));
        assertTrue(CodeDiff.unified("const a = 1;", "").contains("- 1  const a = 1;"));
        assertTrue(CodeDiff.unified(null, "const a = 1;").contains("+ 1  const a = 1;"));
    }
}
