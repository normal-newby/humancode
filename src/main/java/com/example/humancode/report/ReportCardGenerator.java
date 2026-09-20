package com.example.humancode.report;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.humancode.ai.PromptAssembler;
import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.speech.SpeechService;
import com.example.humancode.config.OpenAiClientHolder;
import com.example.humancode.interview.SessionState;
import com.example.humancode.problem.Problem;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
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
     * Far more headroom than the quip path's 400, and most of it is not for the
     * visible answer. {@link #EFFORT} makes this a genuinely reasoning call, and
     * reasoning tokens come out of this same budget — the exact trap CLAUDE.md §5
     * documents, where the call returns 200 with {@code status=incomplete}, no
     * message, and a report that silently comes back canned.
     */
    private static final long MAX_OUTPUT_TOKENS = 6_000L;

    /**
     * Not {@code MINIMAL}, which is right for a heckle and wrong for a verdict.
     *
     * <p>The quip path reasons about nothing: it looks at a diff and lands a
     * joke. This call has to check every rubric item against the finished files
     * and decide whether an app works, with no runner to check it (CLAUDE.md §6)
     * — so its judgement is the only verification there is. On minimal effort it
     * was observed asserting that zeroing a quantity left the row on screen, in
     * a session whose render rebuilt the list from a filter on exactly that
     * quantity. That is not a tone problem, it is a wrong verdict delivered
     * confidently, and it is the one failure this whole feature cannot survive.
     *
     * <p>It costs seconds and tokens at the one moment in the session where
     * nobody is typing and a pause reads as deliberation.
     */
    private static final ReasoningEffort EFFORT = ReasoningEffort.MEDIUM;

    /**
     * The closing bump is clamped to the range the schema asks for rather than
     * trusted. {@code bumpImpatience} already clamps the meter to 0-100, so a
     * runaway value cannot break it, but it could still pin the meter at either
     * end off one number and make every ending look the same.
     */
    private static final int MIN_CLOSING_DELTA = -10;
    private static final int MAX_CLOSING_DELTA = 30;

    /** Matches {@link GeneratedReport#ratingDelta()}'s own documented range. */
    private static final int MIN_RATING_DELTA = -15;
    private static final int MAX_RATING_DELTA = 30;

    private final OpenAiClientHolder clientHolder;
    private final PromptAssembler prompts;
    private final HumancodeProperties props;
    private final ReportCardGuard guard;
    private final SpeechService speech;

    /**
     * The closing reaction still moves the meter. Taking delivery of an app that
     * does not work costs the human their patience, so the number they have been
     * watching all session is where that lands, and it lands before
     * {@link #stats} reads it or the report would show the pre-verdict figure.
     *
     * <p>This is not UI-DESIGN.md §4.7 leaking. §4.7 forbids a <em>verdict</em>
     * on screen, a pass count or a failure list. Impatience is not one: it moved
     * on every reaction all session, and a high final number reads as "you took
     * forever" as readily as "it is broken". {@code outcome} itself, the field
     * that really is a verdict, never leaves this class.
     */
    public ReportCard generate(SessionState state, Problem problem) {
        Optional<OpenAIClient> client = clientHolder.client();
        GeneratedReport content = client.isPresent() ? callModel(client.get(), state, problem) : null;

        boolean canned = content == null;
        GeneratedReport safe = canned ? CannedReportCard.forSession(state, problem) : content;

        int impatience = state.bumpImpatience(
                Math.clamp(safe.impatienceDelta(), MIN_CLOSING_DELTA, MAX_CLOSING_DELTA));
        int ratingDelta = Math.clamp(safe.ratingDelta(), MIN_RATING_DELTA, MAX_RATING_DELTA);

        // Read aloud off the meter, never off `outcome`. The reasoning is the
        // same as the report card's face (UI-DESIGN.md §6a): outcome knows
        // whether the app works and deliberately never leaves this class, so a
        // delivery chosen by it would be the pass/fail badge §4.7 forbids —
        // announced out loud, which is worse than drawn. Started here so the
        // clip is in flight while the verdict types itself out on screen.
        String speechId = speak(state, safe.verdict(), impatience);

        return new ReportCard(
                safe.verdict(),
                safe.insults(),
                safe.compliments(),
                problem.similarProblems() == null ? List.of() : problem.similarProblems(),
                stats(state),
                ratingDelta,
                speechId,
                canned);
    }

    /** @return the id the browser fetches the clip by, or null when running silent */
    private String speak(SessionState state, String verdict, int impatience) {
        if (!speech.configured()) {
            return null;
        }
        String speechId = "verdict-" + state.sessionId();
        speech.prepareVerdict(speechId, state.sessionId(), verdict, impatience);
        return speechId;
    }

    /** @return the generated report, or {@code null} on any failure — caller falls back to canned. */
    private GeneratedReport callModel(OpenAIClient client, SessionState state, Problem problem) {
        try {
            StructuredResponseCreateParams<GeneratedReport> params = ResponseCreateParams.builder()
                    .model(props.ai().model())
                    .instructions(prompts.instructions(state, problem))
                    .input(prompts.reportInput(state))
                    .reasoning(Reasoning.builder().effort(EFFORT).build())
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .text(GeneratedReport.class)
                    .build();

            // Its own deadline, not the client-wide 30s. A reasoning call runs past
            // that, and the client-wide timeout does not fail a slow call, it
            // retries it — so the short deadline costs three attempts and still
            // ends in the canned report (CLAUDE.md §6).
            RequestOptions options = RequestOptions.builder()
                    .timeout(props.ai().reportTimeout())
                    .build();

            long started = System.nanoTime();
            StructuredResponse<GeneratedReport> response = client.responses().create(params, options);
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

            Optional<String> rejected = guard.reject(report.get());
            if (rejected.isPresent()) {
                // Say which rule and quote the text. The fallback that follows is
                // silent everywhere else, so this line is the only evidence the
                // model ever wrote anything at all.
                log.warn("Rejected the generated report for session {}: {}",
                        state.sessionId(), rejected.get());
                return null;
            }

            // The outcome is the one thing that explains a surprising ending and the
            // one thing the candidate never sees, so the log is the only place it
            // is readable at all.
            log.info("Generated report card for session {} in {}ms (outcome={}, impatienceDelta={}, ratingDelta={})",
                    state.sessionId(), millis, report.get().outcome(), report.get().impatienceDelta(),
                    report.get().ratingDelta());
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
                state.submitCount(),
                state.impatience());
    }
}
