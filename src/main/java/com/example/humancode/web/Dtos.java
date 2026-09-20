package com.example.humancode.web;

import java.util.List;
import java.util.Map;

import com.example.humancode.interview.Utterance;
import com.example.humancode.problem.Problem;
import com.example.humancode.report.ReportCard;
import com.example.humancode.telemetry.EventType;
import com.example.humancode.user.LeaderboardEntry;
import com.example.humancode.user.UserProfile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Request and response shapes for the REST surface. */
public final class Dtos {

    private Dtos() {
    }

    /**
     * @param difficulty {@code very-easy}, {@code easy}, {@code medium} or
     *                   {@code hard}. Anything else, including null, means
     *                   "surprise me" — the behaviour from before the selector
     *                   existed.
     * @param handle     who is playing, or null to play anonymously
     * @param token      the secret that came back when the handle was claimed.
     *                   A handle without a matching token is not an error — the
     *                   session just starts anonymous and moves no rating.
     */
    public record StartSessionRequest(String problemId, String language, String difficulty, String problemType,
            String handle, String token) {
    }

    public record ClaimUserRequest(String handle) {
    }

    public record ResumeUserRequest(String handle, String token) {
    }

    /**
     * The response to a claim, and the only time a token is ever on the wire in
     * this direction. {@code resume} answers with a profile alone, because the
     * browser asking already had one.
     */
    public record ClaimUserResponse(UserProfile user, String token) {
    }

    /**
     * @param entries the top rows, already ranked
     * @param total   how many candidates have finished a session at all, so the
     *                board can say what it is a top-20 <em>of</em>
     * @param you     the viewer's own standing, whether or not they made the
     *                cut — null when nobody is signed in
     */
    public record LeaderboardResponse(List<LeaderboardEntry> entries, long total, UserProfile you) {
    }

    public record SessionResponse(
            String sessionId,
            Problem problem,
            String language,
            String phase,
            int impatience,
            List<Utterance> transcript,
            List<String> notes,
            boolean live,
            /** Only populated by {@code POST /sessions/{id}/finish}. */
            ReportCard report,
            /**
             * Where to fetch the problem statement read aloud, or null when the
             * app is running silent. Synthesis began when the session was
             * created, so by the time the prelude hands over it is normally
             * waiting — see {@code speech/SpeechService}.
             */
            String statementSpeechId,
            /**
             * Whoever this session counts for. On {@code /finish} it is their
             * standing <em>after</em> the report's rating delta has landed —
             * the server is the one that applies it (see {@code UserService}),
             * so this is the number, not a suggestion the client adds up
             * itself. Null for an anonymous session.
             */
            UserProfile user) {
    }

    /**
     * One batch of editor telemetry. The client buffers for ~1.5s and sends
     * these in bulk — never one request per keystroke.
     *
     * <p>{@code files} carries every file's full content, keyed by filename, not
     * just whichever one was actively being edited. If a batch only carried the
     * active file, editing file A then switching to file B inside the same
     * flush window would leave the server's copy of A silently stale — the
     * interviewer would judge against outdated content while the character
     * counts said something had changed. These are small scaffold files, so
     * sending the full map every non-empty flush is cheap and removes that bug
     * class entirely.
     */
    public record TelemetryBatch(
            @NotNull List<TelemetryItem> events,
            Map<String, String> files) {
    }

    public record TelemetryItem(
            @NotNull EventType type,
            @PositiveOrZero long inserted,
            @PositiveOrZero long deleted,
            /** Which file the edit happened in — replay-log detail, not state. */
            String file,
            String detail) {
    }

    /**
     * @param text the hint itself
     * @param hintsUsed 1-based number of the hint just spent
     * @param hintsRemaining how many are left this session, {@code SessionState.MAX_HINTS} at most
     * @param canned true when the model was unavailable, failed, or got rejected by the guard
     */
    public record HintResponse(String text, int hintsUsed, int hintsRemaining, boolean canned) {
    }

    public record MetricsResponse(
            long elapsedSeconds,
            long idleSeconds,
            long charsInserted,
            long charsDeleted,
            double deleteRatio,
            int pasteCount,
            int submitCount,
            int impatience) {
    }
}
