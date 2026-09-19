package com.example.humancode.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.humancode.interview.SessionState;
import com.example.humancode.problem.Problem;

class CannedReportCardTest {

    @Test
    void callsOutEachUntouchedScaffoldGapWithoutGivingCreditForSubmitting() {
        Problem problem = problem("// TODO one\n// TODO two\n// TODO three\n// TODO four\n// TODO five\n");
        SessionState state = new SessionState("session", problem, "javascript");
        state.code("app.js", "done\n// TODO two\n// TODO three\n// TODO four\n// TODO five\n");
        state.recordSubmit();

        GeneratedReport report = CannedReportCard.forSession(state, problem);

        assertTrue(report.verdict().contains("4 of 5 marked gaps"));
        assertEquals(List.of(), report.compliments());
    }

    @Test
    void neverClaimsThatChangingFilesProvesTheAppWorks() {
        Problem problem = problem("// TODO one\n");
        SessionState state = new SessionState("session", problem, "javascript");
        state.code("app.js", "done\n");
        state.recordSubmit();

        GeneratedReport report = CannedReportCard.forSession(state, problem);

        assertTrue(report.verdict().contains("remains unproven"));
        assertEquals(List.of(), report.compliments());
    }

    private Problem problem(String starter) {
        return new Problem("test", "Test", "easy", List.of(), "Build it.",
                List.of(new Problem.ProblemFile("app.js", "javascript", starter, "done\n")),
                List.of("First requirement", "Second requirement", "Third requirement", "Fourth requirement",
                        "Fifth requirement"),
                List.of(), List.of());
    }
}
