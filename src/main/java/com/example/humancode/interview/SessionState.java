package com.example.humancode.interview;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.example.humancode.problem.Problem;

/**
 * The live, in-memory view of one interview. This is the hot object: telemetry
 * updates it many times a second, and the trigger engine reads it on a timer.
 *
 * <p>It is deliberately <em>not</em> a JPA entity. Writing to SQLite on every
 * keystroke would be pointless and slow; {@code SessionService} snapshots this
 * to the database on phase transitions and at the end of the session.
 */
public final class SessionState {

    private final String sessionId;
    /**
     * The whole problem, not just an id. A generated problem exists only for
     * the life of its session, so there is nothing to look it up in.
     */
    private final Problem problem;
    private final Instant startedAt;

    private volatile Phase phase = Phase.INTRO;
    /** Filename -> current content, one entry per {@link Problem#files()}. */
    private final Map<String, String> code = new ConcurrentHashMap<>();
    /**
     * Filename -> content as it stood the last time the interviewer spoke. The
     * prompt diffs this against {@link #code} so a reaction can be about what
     * just changed rather than about the same shape of code as last time.
     */
    private final Map<String, String> previousCode = new ConcurrentHashMap<>();
    private volatile String language;

    private volatile Instant lastEventAt;
    private volatile Instant firstKeystrokeAt;
    private volatile Instant lastKeystrokeAt;
    private volatile Instant lastUtteranceAt;

    /**
     * Within this long of the last keystroke the candidate counts as still
     * typing. Comfortably longer than one telemetry batch, so a fragment a batch
     * caught is still recognised as in flight when the reaction is assembled;
     * far shorter than the idle threshold, so a line they walked away from stops
     * being protected by it.
     */
    private static final Duration STILL_TYPING = Duration.ofSeconds(4);

    /** 0-100. Drives the meter, the avatar, and the interviewer's tone. */
    private final AtomicInteger impatience = new AtomicInteger(0);

    private final AtomicLong charsInserted = new AtomicLong();
    private final AtomicLong charsDeleted = new AtomicLong();
    private final AtomicInteger pasteCount = new AtomicInteger();
    private final AtomicLong pastedChars = new AtomicLong();
    /** How many times the candidate has handed the turn back for judgment. */
    private final AtomicInteger submitCount = new AtomicInteger();

    /** Hard cap on hints per session — asking for a third is a 409, not a fourth call. */
    public static final int MAX_HINTS = 2;
    private final AtomicInteger hintsUsed = new AtomicInteger();

    /** What the interviewer has said, in order. */
    private final List<Utterance> transcript = new CopyOnWriteArrayList<>();
    /** The visible "private notes" sidebar. */
    private final List<String> notes = new CopyOnWriteArrayList<>();
    /** Triggers already fired, so one-shot triggers do not repeat. */
    private final List<String> firedOnce = new CopyOnWriteArrayList<>();
    /**
     * Curveball text actually delivered to the candidate this session, in
     * order — not just picked by the trigger engine, but confirmed spoken by
     * {@code InterviewDirector.fire()}. See {@code PromptAssembler}: every
     * judgment after this point, quip or report card, has to be graded
     * against the amended scope, not the original rubric alone, and the
     * cached prompt prefix is fixed at session start — this is how a
     * mid-session scope change reaches a prompt built before it existed.
     */
    private final List<String> curveballsIssued = new CopyOnWriteArrayList<>();

    public SessionState(String sessionId, Problem problem, String language) {
        this.sessionId = sessionId;
        this.problem = problem;
        this.language = language;
        for (Problem.ProblemFile file : problem.files()) {
            code.put(file.name(), file.starterContent());
            previousCode.put(file.name(), file.starterContent());
        }
        this.startedAt = Instant.now();
        this.lastEventAt = this.startedAt;
    }

    // --- identity -----------------------------------------------------------

    public String sessionId() {
        return sessionId;
    }

    public Problem problem() {
        return problem;
    }

    public String problemId() {
        return problem.id();
    }

    public Instant startedAt() {
        return startedAt;
    }

    // --- mutable view -------------------------------------------------------

    public Phase phase() {
        return phase;
    }

    public void phase(Phase phase) {
        this.phase = phase;
    }

    public String code(String file) {
        return code.get(file);
    }

    public void code(String file, String content) {
        code.put(file, content);
    }

    /** Every file's current content, in the problem's declared order. */
    public Map<String, String> code() {
        return orderedCopy(code);
    }

    /** Every file's content as of the interviewer's last line. Never null entries. */
    public Map<String, String> previousCode() {
        return orderedCopy(previousCode);
    }

    /**
     * The buffers with any line they are <em>still typing</em> left off.
     *
     * <p>This is what the interviewer is shown, and the reason is simple: a line
     * under someone's fingers is not a decision they have made. Telemetry lands
     * in 1.5-second batches, so a batch that carries a finished line often
     * carries the first few characters of the next one too, and a model handed
     * {@code const subtot} will say something about {@code const subtot} — which
     * reads as the interviewer jogging your elbow rather than reading your work.
     * Telling it not to in the prompt was tried first and lost to the stronger
     * instruction a few lines further down to react to what just changed. So the
     * fragment does not go in the prompt at all.
     *
     * <p>Only while they are typing. Past {@link #STILL_TYPING} the same
     * fragment is not in flight, it is abandoned, and an abandoned half-line is
     * exactly the sort of thing the interviewer should be asking about — so it
     * comes back into view, on its own, with no rule anywhere to special-case
     * it.
     */
    public Map<String, String> settledCode() {
        Map<String, String> settled = orderedCopy(code);
        if (!stillTyping()) {
            return settled;
        }
        settled.replaceAll((file, content) -> settle(content));
        return settled;
    }

    /**
     * Whether a trailing fragment is under their fingers right now.
     *
     * <p>Having never typed at all counts as not typing. Without that clause the
     * fallback inside {@link #idleFor()} measures from the start of the session,
     * so a candidate who has not touched the keyboard looks like the busiest
     * person in the room for the first four seconds and the starter file's last
     * line gets held back for no reason.
     */
    private boolean stillTyping() {
        return firstKeystrokeAt != null && idleFor().compareTo(STILL_TYPING) < 0;
    }

    /** The names of any files currently holding a line mid-typing, in order. */
    public List<String> filesMidLine() {
        if (!stillTyping()) {
            return List.of();
        }
        List<String> midLine = new java.util.ArrayList<>();
        orderedCopy(code).forEach((file, content) -> {
            if (content != null && !content.equals(settle(content))) {
                midLine.add(file);
            }
        });
        return List.copyOf(midLine);
    }

    /** Everything up to and including the last newline, or the lot if it ends on one. */
    private static String settle(String content) {
        if (content == null || content.isEmpty() || content.endsWith("\n")) {
            return content;
        }
        int lastBreak = content.lastIndexOf('\n');
        if (lastBreak < 0) {
            // A single unfinished line and nothing else. Hiding it would show the
            // interviewer an empty file and provoke "you have written nothing",
            // which is worse than showing the fragment.
            return content;
        }
        return content.substring(0, lastBreak + 1);
    }

    /**
     * Moves the diff baseline up to the current buffers. Called once the
     * interviewer has actually spoken, so the next reaction sees only what
     * happened after this line — never call it on a suppressed trigger, or the
     * work done in between becomes invisible.
     *
     * <p>It records the <em>settled</em> buffers, matching what was actually
     * shown. Baseline on the raw buffer instead and the half-line that was
     * hidden this time reappears in the next diff as a deletion, so the
     * interviewer ends up reacting to the fragment anyway, one line late.
     */
    public void markCodeSpokenFor() {
        previousCode.putAll(settledCode());
    }

    private Map<String, String> orderedCopy(Map<String, String> source) {
        Map<String, String> ordered = new LinkedHashMap<>();
        for (Problem.ProblemFile file : problem.files()) {
            ordered.put(file.name(), source.get(file.name()));
        }
        return ordered;
    }

    public String language() {
        return language;
    }

    public void language(String language) {
        this.language = language;
    }

    public int impatience() {
        return impatience.get();
    }

    /** Clamped to 0-100 so a runaway delta cannot break the meter. */
    public int bumpImpatience(int delta) {
        return impatience.updateAndGet(current -> Math.clamp(current + delta, 0, 100));
    }

    // --- telemetry counters -------------------------------------------------

    public void recordEdit(long inserted, long deleted) {
        Instant now = Instant.now();
        charsInserted.addAndGet(inserted);
        charsDeleted.addAndGet(deleted);
        lastEventAt = now;
        lastKeystrokeAt = now;
        if (firstKeystrokeAt == null) {
            firstKeystrokeAt = now;
        }
        if (phase == Phase.INTRO) {
            phase = Phase.CODING;
        }
    }

    public void recordPaste(long chars) {
        pasteCount.incrementAndGet();
        pastedChars.addAndGet(chars);
        // Monaco reports the paste as a content change first, so these
        // characters have already been counted as typed. Pasted text is not
        // written text — leaving it in would flatter the delete ratio and hide
        // exactly the behaviour we care about.
        charsInserted.updateAndGet(current -> Math.max(0, current - chars));
        lastEventAt = Instant.now();
    }

    public void recordSubmit() {
        submitCount.incrementAndGet();
        lastEventAt = Instant.now();
    }

    public void touch() {
        lastEventAt = Instant.now();
    }

    public long charsInserted() {
        return charsInserted.get();
    }

    public long charsDeleted() {
        return charsDeleted.get();
    }

    public int pasteCount() {
        return pasteCount.get();
    }

    public long pastedChars() {
        return pastedChars.get();
    }

    public int submitCount() {
        return submitCount.get();
    }

    /**
     * @return the 1-based number of the hint just spent (1 or {@link #MAX_HINTS})
     * @throws HintsExhaustedException if every hint this session already has a spent one
     */
    public int recordHint() {
        int used = hintsUsed.updateAndGet(current -> {
            if (current >= MAX_HINTS) {
                throw new HintsExhaustedException();
            }
            return current + 1;
        });
        lastEventAt = Instant.now();
        return used;
    }

    public int hintsUsed() {
        return hintsUsed.get();
    }

    public int hintsRemaining() {
        return Math.max(0, MAX_HINTS - hintsUsed.get());
    }

    /** Thrown by {@link #recordHint()} once every hint this session has been spent. */
    public static final class HintsExhaustedException extends RuntimeException {
    }

    // --- derived ------------------------------------------------------------

    public Duration idleFor() {
        Instant reference = lastKeystrokeAt != null ? lastKeystrokeAt : startedAt;
        return Duration.between(reference, Instant.now());
    }

    public Duration elapsed() {
        return Duration.between(startedAt, Instant.now());
    }

    public Duration sinceLastUtterance() {
        return lastUtteranceAt == null
                ? Duration.between(startedAt, Instant.now())
                : Duration.between(lastUtteranceAt, Instant.now());
    }

    public Instant firstKeystrokeAt() {
        return firstKeystrokeAt;
    }

    public Instant lastEventAt() {
        return lastEventAt;
    }

    /**
     * Fraction of typed characters that were later deleted. High values mean
     * thrashing — the candidate is rewriting rather than progressing.
     */
    public double deleteRatio() {
        long inserted = charsInserted.get();
        return inserted == 0 ? 0 : (double) charsDeleted.get() / inserted;
    }

    // --- transcript / notes -------------------------------------------------

    public void addUtterance(Utterance utterance) {
        transcript.add(utterance);
        lastUtteranceAt = Instant.now();
    }

    public List<Utterance> transcript() {
        return List.copyOf(transcript);
    }

    /**
     * Appends a note unless it repeats the previous one.
     *
     * <p>A column of four identical "idle again." lines reads as a broken
     * feature rather than a joke, and the canned fallback repeats itself by
     * construction.
     *
     * @return true if the note was actually added
     */
    public boolean addNote(String note) {
        if (!notes.isEmpty() && notes.get(notes.size() - 1).equalsIgnoreCase(note)) {
            return false;
        }
        notes.add(note);
        return true;
    }

    public List<String> notes() {
        return List.copyOf(notes);
    }

    /**
     * Records a curveball as actually delivered. Called from
     * {@code InterviewDirector.fire()}, after the same cooldown and SSE
     * guards that gate every other side effect of a spoken line — a
     * curveball that never reached a connected candidate must not start
     * being graded on.
     */
    public void recordCurveball(String text) {
        curveballsIssued.add(text);
    }

    public List<String> curveballsIssued() {
        return List.copyOf(curveballsIssued);
    }

    /** @return true the first time this key is seen, false forever after. */
    public boolean fireOnce(String key) {
        if (firedOnce.contains(key)) {
            return false;
        }
        firedOnce.add(key);
        return true;
    }
}
