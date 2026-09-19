package com.example.humancode.report;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.humancode.ai.PromptAssembler;
import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.config.OpenAiClientHolder;
import com.example.humancode.interview.SessionState;
import com.example.humancode.problem.Problem;
import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The deliberate path's first tenant (CLAUDE.md §5): one structured call at
 * session end, the full model rather than the quip model. Not streamed yet —
 * real token-by-token streaming onto the SSE channel is flagged in CLAUDE.md
 * §9 as an open decision, not a requirement, and it is a meaningfully bigger
 * lift than this call.
 *
 * <p>Never throws and never returns a report with a blank verdict, the same
 * guarantee {@link com.example.humancode.ai.Interviewer} makes for the quip
 * path: a report card that fails to render is a worse ending than a canned
 * one.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReportCardGenerator {

    /**
     * More headroom than the quip path's 400: a verdict plus up to four
     * insults and two compliments is several times the visible tokens of one
     * reaction line, and this is a reasoning model — see the quip path's own
     * scars (CLAUDE.md §5) for what happens when the cap is too tight.
     */
    private static final long MAX_OUTPUT_TOKENS = 1000L;

    private final OpenAiClientHolder clientHolder;
    private final PromptAssembler prompts;
    private final HumancodeProperties props;
    private final ReportCardGuard guard;

    public ReportCard generate(SessionState state, Problem problem) {
        Optional<OpenAIClient> client = clientHolder.client();
        GeneratedReport content = client.isPresent() ? callModel(client.get(), state, problem) : null;

        boolean canned = content == null;
        GeneratedReport safe = canned ? CannedReportCard.forSession(state) : content;

        return new ReportCard(
                safe.verdict(),
                safe.insults(),
                safe.compliments(),
                problem.similarProblems() == null ? List.of() : problem.similarProblems(),
                stats(state),
                canned);
    }

    /** @return the generated report, or {@code null} on any failure — caller falls back to canned. */
    private GeneratedReport callModel(OpenAIClient client, SessionState state, Problem problem) {
        try {
            StructuredResponseCreateParams<GeneratedReport> params = ResponseCreateParams.builder()
                    .model(props.ai().model())
                    .instructions(prompts.instructions(state, problem))
                    .input(prompts.reportInput(state))
                    .reasoning(Reasoning.builder().effort(ReasoningEffort.MINIMAL).build())
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .text(GeneratedReport.class)
                    .build();

            long started = System.nanoTime();
            StructuredResponse<GeneratedReport> response = client.responses().create(params);
            long millis = (System.nanoTime() - started) / 1_000_000;

            Optional<GeneratedReport> report = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(out -> out.outputText().stream())
                    .findFirst();

            if (report.isEmpty()) {
                log.warn("No structured report for session {} (status={}, incomplete={}, output tokens={}/{})",
                        state.sessionId(),
                        response.rawResponse().status().map(Object::toString).orElse("unknown"),
                        response.rawResponse().incompleteDetails()
                                .flatMap(details -> details.reason())
                                .map(Object::toString)
                                .orElse("none"),
                        response.rawResponse().usage().map(usage -> usage.outputTokens()).orElse(0L),
                        MAX_OUTPUT_TOKENS);
                return null;
            }

            if (!guard.isSafe(report.get())) {
                log.warn("Rejected an unsafe generated report for session {}", state.sessionId());
                return null;
            }

            log.info("Generated report card for session {} in {}ms", state.sessionId(), millis);
            return report.get();

        } catch (RuntimeException e) {
            log.warn("Report generation failed for session {} ({}); falling back to canned",
                    state.sessionId(), e.toString());
            return null;
        }
    }

    private ReportCard.Stats stats(SessionState state) {
        return new ReportCard.Stats(
                state.elapsed().toSeconds(),
                state.charsInserted(),
                state.charsDeleted(),
                state.pasteCount(),
                state.runCount(),
                state.failedRunCount(),
                state.impatience(),
                state.testsEverPassed());
    }
}
