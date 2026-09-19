package com.example.humancode.ai;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.humancode.config.HumancodeProperties;
import com.example.humancode.config.OpenAiClientHolder;
import com.example.humancode.interview.SessionState;
import com.example.humancode.problem.Problem;
import com.example.humancode.telemetry.Trigger;
import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The quip path: one short, in-character reaction per trigger.
 *
 * <p>Deliberately non-streaming and capped short — this fires often and has to
 * feel instant. The deliberate path (problem delivery, hints, report card) is a
 * separate, streamed call.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class Interviewer {

    /**
     * Headroom for the whole response, reasoning included.
     *
     * <p>This used to be 160, which a reasoning model spends entirely on
     * thinking before it writes a single visible token. The call then returns
     * {@code status=incomplete} with a reasoning item and no message, which
     * looks exactly like a model that had nothing to say — every line in the
     * session came back canned while the logs said the call had succeeded.
     */
    private static final long MAX_OUTPUT_TOKENS = 400L;

    private final OpenAiClientHolder clientHolder;
    private final PromptAssembler prompts;
    private final HumancodeProperties props;
    private final ReactionGuard reactionGuard;

    /**
     * Never throws and never returns empty — a session that goes silent because
     * of a network blip is a broken demo, so failures fall back to canned lines.
     */
    public Result react(SessionState state, Problem problem, Trigger trigger) {
        if (trigger.kind() == Trigger.Kind.CURVEBALL) {
            return curveball(trigger);
        }

        Optional<OpenAIClient> client = clientHolder.client();
        if (client.isEmpty()) {
            return new Result(CannedLines.forTrigger(trigger, state.impatience(), state.transcript()), true);
        }

        try {
            StructuredResponseCreateParams<Reaction> params = ResponseCreateParams.builder()
                    .model(props.ai().quipModel())
                    .instructions(prompts.instructions(state, problem))
                    .input(prompts.input(state, trigger))
                    // A heckle is not a reasoning problem, and the candidate is
                    // waiting: minimal effort keeps the budget for the line.
                    .reasoning(Reasoning.builder().effort(ReasoningEffort.MINIMAL).build())
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .text(Reaction.class)
                    .build();

            long started = System.nanoTime();
            StructuredResponse<Reaction> response = client.get().responses().create(params);
            long millis = (System.nanoTime() - started) / 1_000_000;

            Optional<Reaction> reaction = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst();

            if (reaction.isEmpty()) {
                warnEmpty(response, trigger);
                return new Result(CannedLines.forTrigger(trigger, state.impatience(), state.transcript()), true);
            }

            if (!reactionGuard.isSafe(reaction.get())) {
                log.warn("Rejected an unsafe model reaction for trigger {}", trigger.kind());
                return new Result(CannedLines.forTrigger(trigger, state.impatience(), state.transcript()), true);
            }

            logUsage(response, trigger, millis);
            return new Result(reaction.get(), false);

        } catch (RuntimeException e) {
            log.warn("Quip call failed for trigger {} ({}); falling back to a canned line",
                    trigger.kind(), e.toString());
            return new Result(CannedLines.forTrigger(trigger, state.impatience(), state.transcript()), true);
        }
    }

    /**
     * Curveballs are pre-authored, problem-author content — the same trust level
     * as the opening problem statement, which is also delivered verbatim rather
     * than paraphrased by a model call. Delivering it costs nothing: no client
     * needed, no guard needed, no fallback needed, consistent with CLAUDE.md §2 —
     * the model decides <em>what</em> is said only when it actually has to.
     */
    private Result curveball(Trigger trigger) {
        Reaction reaction = new Reaction(trigger.detail(), Reaction.Mood.AMUSED,
                trigger.urgency(), "Sprung a curveball.");
        return new Result(reaction, false);
    }

    /**
     * A response with no message is almost always a truncation, so say which
     * kind. The old one-line warning gave no way to tell "the model declined"
     * from "the budget ran out", and the two need opposite fixes.
     */
    private void warnEmpty(StructuredResponse<Reaction> response, Trigger trigger) {
        var raw = response.rawResponse();
        String status = raw.status().map(Object::toString).orElse("unknown");
        String reason = raw.incompleteDetails()
                .flatMap(details -> details.reason())
                .map(Object::toString)
                .orElse("none");
        long output = raw.usage().map(usage -> usage.outputTokens()).orElse(0L);

        log.warn("No structured reaction for trigger {} (status={}, incomplete={}, output tokens={}/{})."
                + " If incomplete=max_output_tokens, the model spent the budget on reasoning:"
                + " raise MAX_OUTPUT_TOKENS or lower the reasoning effort.",
                trigger.kind(), status, reason, output, MAX_OUTPUT_TOKENS);
    }

    /**
     * Cached-token count is the number to watch. If it stays at zero across
     * consecutive quips in one session, something in the prompt prefix is
     * moving and every call is paying full price.
     */
    private void logUsage(StructuredResponse<Reaction> response, Trigger trigger, long millis) {
        response.rawResponse().usage().ifPresentOrElse(usage -> {
            long cached = usage.inputTokensDetails().cachedTokens();
            log.debug("quip trigger={} latency={}ms input={} cached={} output={}",
                    trigger.kind(), millis, usage.inputTokens(), cached, usage.outputTokens());
            if (cached == 0) {
                log.debug("No cached prompt tokens — check PromptAssembler's prefix for moving bytes.");
            }
        }, () -> log.debug("quip trigger={} latency={}ms (no usage reported)", trigger.kind(), millis));
    }

    /** @param canned true when this line came from the fallback, not the model. */
    public record Result(Reaction reaction, boolean canned) {
    }
}
