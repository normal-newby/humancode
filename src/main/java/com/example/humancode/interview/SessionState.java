package com.example.humancode.interview;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private volatile String code;
    /**
     * The buffer as it stood the last time the interviewer spoke. The prompt
     * diffs this against {@link #code} so a reaction can be about what just
     * changed rather than about the same shape of code as last time.
     */
    private volatile String previousCode;
    private volatile String language;

    private volatile Instant lastEventAt;
    private volatile Instant firstKeystrokeAt;
    private volatile Instant lastKeystrokeAt;
    private volatile Instant lastUtteranceAt;

    /** 0-100. Drives the meter, the avatar, and the interviewer's tone. */
    private final AtomicInteger impatience = new AtomicInteger(0);

    private final AtomicLong charsInserted = new AtomicLong();
    private final AtomicLong charsDeleted = new AtomicLong();
    private final AtomicInteger pasteCount = new AtomicInteger();
    private final AtomicLong pastedChars = new AtomicLong();
    private final AtomicInteger runCount = new AtomicInteger();
    private final AtomicInteger failedRunCount = new AtomicInteger();

    /** What the interviewer has said, in order. */
    private final List<Utterance> transcript = new CopyOnWriteArrayList<>();
    /** The visible "private notes" sidebar. */
    private final List<String> notes = new CopyOnWriteArrayList<>();
    /** Triggers already fired, so one-shot triggers do not repeat. */
    private final List<String> firedOnce = new CopyOnWriteArrayList<>();

    public SessionState(String sessionId, Problem problem, String language) {
        this.sessionId = sessionId;
        this.problem = problem;
        this.language = language;
        this.code = problem.starterCode();
        this.previousCode = problem.starterCode();
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

    public String code() {
        return code;
    }

    public void code(String code) {
        this.code = code;
    }

    /** The buffer as of the interviewer's last line. Never null. */
    public String previousCode() {
        return previousCode;
    }

    /**
     * Moves the diff baseline up to the current buffer. Called once the
     * interviewer has actually spoken, so the next reaction sees only what
     * happened after this line — never call it on a suppressed trigger, or the
     * work done in between becomes invisible.
     */
    public void markCodeSpokenFor() {
        this.previousCode = this.code;
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

    public void recordRun(boolean passed) {
        runCount.incrementAndGet();
        if (!passed) {
            failedRunCount.incrementAndGet();
        }
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

    public int runCount() {
        return runCount.get();
    }

    public int failedRunCount() {
        return failedRunCount.get();
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

    /** Whether at least one run during the session actually passed. */
    public boolean testsEverPassed() {
        return runCount.get() - failedRunCount.get() > 0;
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

    /** @return true the first time this key is seen, false forever after. */
    public boolean fireOnce(String key) {
        if (firedOnce.contains(key)) {
            return false;
        }
        firedOnce.add(key);
        return true;
    }
}
