package com.example.humancode.report;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.openai.models.responses.ResponseCreateParams;

/**
 * A rejected report is not an error, it is the canned one, so every rule in
 * {@link ReportCardGuard} fails silently. These pin the two that the closing
 * reaction's register actually depends on.
 */
class ReportCardGuardTest {

    private final ReportCardGuard guard = new ReportCardGuard();

    @Test
    @DisplayName("A curt, understated verdict is the point, not a defect")
    void acceptsTheUnderstatedRegister() {
        assertTrue(guard.isSafe(report(GeneratedReport.Outcome.WORKS, "Hmm. Not bad.", -6)));
    }

    @Test
    @DisplayName("An annoyed verdict about their own broken app passes too")
    void acceptsTheAnnoyedRegister() {
        assertTrue(guard.isSafe(report(GeneratedReport.Outcome.BROKEN,
                "What is this? My app does not work. I clicked the button and nothing happened.", 26)));
    }

    @Test
    @DisplayName("a working app with nothing to complain about is a valid report")
    void acceptsAReportWithNoInsults() {
        // The bug this pins cost a real session: the model judged the app to be
        // working, stayed unimpressed and returned no barbs, a floor of one
        // insult rejected the whole thing, and the canned fallback then told a
        // candidate with working code that their app did not work.
        GeneratedReport report = new GeneratedReport(GeneratedReport.Outcome.WORKS,
                "Hmm. Not bad.", List.of(), List.of("You got there eventually."), -5, 10);

        assertTrue(guard.isSafe(report), () -> guard.reject(report).orElse(""));
    }

    @Test
    @DisplayName("a rejection says which rule fired and quotes the text")
    void rejectionNamesTheRule() {
        String reason = guard.reject(report(GeneratedReport.Outcome.BROKEN,
                "One. Two. Three. Four. Five.", 20)).orElseThrow();

        assertTrue(reason.contains("sentences"), reason);
        assertTrue(reason.contains("One. Two. Three. Four. Five."), reason);
    }

    @Test
    void stillRejectsAnEssay() {
        String sprawl = "You did a thing. ".repeat(4)
                + "And then you did many more things that all took a very long time indeed to arrive at. "
                + "None of which I asked for, and all of which I now have to read through myself today.";
        assertFalse(guard.isSafe(report(GeneratedReport.Outcome.PARTIAL, sprawl, 0)));
    }

    @Test
    void rejectsAReportWithNoOutcome() {
        assertFalse(guard.isSafe(report(null, "Hmm. Not bad.", 0)));
    }

    @Test
    @DisplayName("GeneratedReport produces a schema the Responses API will accept")
    void schemaIsValid() {
        // Validated locally when the params are built — no network, no key. The
        // alternative is finding out as a 400 mid-demo.
        assertDoesNotThrow(() -> ResponseCreateParams.builder()
                .model("gpt-5")
                .instructions("You are running an interview.")
                .input("The session is over.")
                .maxOutputTokens(1_600L)
                .text(GeneratedReport.class)
                .build());
    }

    private GeneratedReport report(GeneratedReport.Outcome outcome, String verdict, int delta) {
        return new GeneratedReport(outcome, verdict, List.of("You took your time about it."), List.of(), delta, 0);
    }
}
