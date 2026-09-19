package com.example.humancode.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        // Half-finished scaffolding in an app they have to use is the annoyed
        // register, and annoyance costs them patience.
        assertEquals(GeneratedReport.Outcome.BROKEN, report.outcome());
        assertTrue(report.impatienceDelta() > 15);
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
        // The canned path can never prove an app works, so it never awards WORKS
        // and never spends the understated register on one.
        assertEquals(GeneratedReport.Outcome.PARTIAL, report.outcome());
    }

    @Test
    void neverAwardsWorksFromFileContentsAlone() {
        Problem problem = problem("// TODO one\n");
        SessionState state = new SessionState("session", problem, "javascript");
        state.code("app.js", "done\n");

        for (int submits = 0; submits < 3; submits++) {
            GeneratedReport report = CannedReportCard.forSession(state, problem);
            assertNotEquals(GeneratedReport.Outcome.WORKS, report.outcome());
            state.recordSubmit();
        }
    }

    @Test
    void everyCannedReportPassesTheGuardItBypasses() {
        ReportCardGuard guard = new ReportCardGuard();
        Problem problem = problem("// TODO one\n// TODO two\n");

        // Each branch of the fallback, in order: nothing submitted, gaps left
        // open, a file handed back untouched, everything touched.
        SessionState untouched = new SessionState("a", problem, "javascript");

        SessionState gapsLeft = new SessionState("b", problem, "javascript");
        gapsLeft.code("app.js", "done\n// TODO two\n");
        gapsLeft.recordSubmit();

        SessionState finished = new SessionState("c", problem, "javascript");
        finished.code("app.js", "done\n");
        finished.recordSubmit();

        for (SessionState state : List.of(untouched, gapsLeft, finished)) {
            GeneratedReport report = CannedReportCard.forSession(state, problem);
            // The fallback never goes through the guard at runtime, so nothing
            // would catch it drifting out of the voice the guard defines.
            assertTrue(guard.isSafe(report), () -> "guard rejected canned report: " + report.verdict());
        }
    }

    private Problem problem(String starter) {
        return new Problem("test", "Test", "easy", List.of(), "Build it.",
                List.of(new Problem.ProblemFile("app.js", "javascript", starter, "done\n")),
                List.of("First requirement", "Second requirement", "Third requirement", "Fourth requirement",
                        "Fifth requirement"),
                List.of(), List.of());
    }
}
