package com.example.humancode.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Claims handles, applies ratings and builds the board.
 *
 * <p>The one rule worth stating out loud: <b>a rating delta is applied here and
 * only here, from a report the server has just generated.</b> It never arrives
 * on a request. The client is told what its number became; it is never asked.
 * That is what keeps the board meaning anything with an auth model this thin —
 * you can lose your own handle by losing a token, but you cannot type yourself
 * to the top of the list.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    /**
     * A row appears on the board once it has been judged at least once, so
     * claiming a handle and walking away puts you nowhere. Expressed as a
     * "greater than" bound because that is the shape Spring Data derives.
     */
    private static final int MIN_SESSIONS_FOR_BOARD = 0;

    public static final int DEFAULT_BOARD_SIZE = 20;
    private static final int MAX_BOARD_SIZE = 100;

    private final UserRepository users;

    /**
     * Claims a handle nobody holds yet.
     *
     * @return the new user, whose token the caller must hand back to the browser
     *         once — it is the only time it exists outside the database.
     * @throws Handles.InvalidHandleException if the handle is not one
     * @throws HandleTakenException if somebody got there first
     */
    @Transactional
    public User claim(String rawHandle) {
        String handle = Handles.require(rawHandle);
        if (users.existsByHandle(handle)) {
            throw new HandleTakenException(handle);
        }
        Instant now = Instant.now();
        User user = new User(UUID.randomUUID().toString(), handle, UUID.randomUUID().toString(), now);
        try {
            users.save(user);
        } catch (DataIntegrityViolationException e) {
            // The unique index, not the existsByHandle above: two tabs claiming
            // the same handle in the same second both pass that check.
            throw new HandleTakenException(handle);
        }
        log.info("Handle claimed: {}", handle);
        return user;
    }

    /**
     * Proves a browser still owns the handle it says it does, and touches
     * lastSeenAt while it is here — the board prints it.
     *
     * @throws UnauthorizedException when the handle is unknown or the token is wrong
     */
    @Transactional
    public User resume(String rawHandle, String token) {
        String handle = Handles.normalize(rawHandle);
        User user = users.findByHandle(handle)
                .filter(candidate -> matches(candidate.getToken(), token))
                .orElseThrow(() -> new UnauthorizedException(handle));
        user.setLastSeenAt(Instant.now());
        return users.save(user);
    }

    /**
     * The same check as {@link #resume}, without the touch and without the
     * throw — for the session-start path, where an identity that no longer
     * verifies means "play anonymously", not "refuse to start an interview".
     */
    @Transactional(readOnly = true)
    public Optional<User> verify(String rawHandle, String token) {
        if (rawHandle == null || rawHandle.isBlank() || token == null || token.isBlank()) {
            return Optional.empty();
        }
        return users.findByHandle(Handles.normalize(rawHandle))
                .filter(candidate -> matches(candidate.getToken(), token));
    }

    /**
     * Folds one finished session into a candidate's standing.
     *
     * <p>The delta is the report card's own, already clamped to -15..30 by
     * {@code ReportCardGenerator} — clamped there rather than here because that
     * is where a runaway model value would otherwise reach the candidate's
     * screen as well as the database.
     */
    @Transactional
    public UserProfile recordSession(String userId, int ratingDelta) {
        User user = users.findById(userId).orElseThrow(() -> new UnknownUserException(userId));
        int before = user.getRating();
        user.setRating(before + ratingDelta);
        user.setPeakRating(Math.max(user.getPeakRating(), user.getRating()));
        user.setSessionsCompleted(user.getSessionsCompleted() + 1);
        user.setLastSeenAt(Instant.now());
        users.save(user);
        log.info("Rating for {}: {} -> {} ({}{}) over {} session(s)",
                user.getHandle(), before, user.getRating(),
                ratingDelta >= 0 ? "+" : "", ratingDelta, user.getSessionsCompleted());
        return profile(user);
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> profileFor(String rawHandle) {
        return users.findByHandle(Handles.normalize(rawHandle)).map(this::profile);
    }

    /** By id, for the session path, which carries an id rather than a handle. */
    @Transactional(readOnly = true)
    public Optional<UserProfile> profileById(String userId) {
        return users.findById(userId).map(this::profile);
    }

    /** 1-based, ties shared; 0 for a candidate no session has judged yet. */
    @Transactional(readOnly = true)
    public UserProfile profile(User user) {
        return UserProfile.of(user, rankOf(user));
    }

    private int rankOf(User user) {
        if (user.getSessionsCompleted() <= MIN_SESSIONS_FOR_BOARD) {
            return 0;
        }
        return (int) users.countBySessionsCompletedGreaterThanAndRatingGreaterThan(
                MIN_SESSIONS_FOR_BOARD, user.getRating()) + 1;
    }

    /**
     * The top rows, ranked.
     *
     * <p>{@code viewerHandle} marks one row as theirs so the client does not
     * have to compare handles to find itself. A viewer below the cut simply is
     * not in the list — {@link #profileFor} is how the UI shows them their own
     * standing when the board does not reach them.
     */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(int limit, String viewerHandle) {
        int size = Math.clamp(limit, 1, MAX_BOARD_SIZE);
        List<User> rows = users.findBySessionsCompletedGreaterThanOrderByRatingDescSessionsCompletedAscCreatedAtAsc(
                MIN_SESSIONS_FOR_BOARD, PageRequest.of(0, size));
        return rank(rows, Handles.normalize(viewerHandle));
    }

    /**
     * Ranks by walking the ordered page rather than counting per row: the order
     * already is the ranking, and one query beats a page of them on a database
     * that allows a single writer.
     */
    private List<LeaderboardEntry> rank(List<User> rows, String viewer) {
        List<LeaderboardEntry> entries = new ArrayList<>(rows.size());
        int rank = 0;
        Integer previousRating = null;
        for (int i = 0; i < rows.size(); i++) {
            User user = rows.get(i);
            // Ties share a rank, and the next distinct rating skips the ones
            // they used up — 1, 2, 2, 4, the way a scoreboard reads.
            if (previousRating == null || user.getRating() != previousRating) {
                rank = i + 1;
                previousRating = user.getRating();
            }
            entries.add(new LeaderboardEntry(
                    rank,
                    user.getHandle(),
                    user.getRating(),
                    user.getPeakRating(),
                    user.getSessionsCompleted(),
                    user.getLastSeenAt(),
                    !viewer.isEmpty() && viewer.equals(user.getHandle())));
        }
        return List.copyOf(entries);
    }

    @Transactional(readOnly = true)
    public long boardSize() {
        return users.countBySessionsCompletedGreaterThan(MIN_SESSIONS_FOR_BOARD);
    }

    /**
     * Length-independent comparison. The token is a random UUID rather than a
     * password, so this is belt and braces — but it costs three lines.
     */
    private boolean matches(String stored, String offered) {
        if (offered == null || stored.length() != offered.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < stored.length(); i++) {
            diff |= stored.charAt(i) ^ offered.charAt(i);
        }
        return diff == 0;
    }

    /** 409. Somebody is already that. */
    public static class HandleTakenException extends RuntimeException {
        public HandleTakenException(String handle) {
            super("Handle already claimed: " + handle);
        }
    }

    /** 403. The handle exists; this browser is not it. */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String handle) {
            super("Not your handle: " + handle);
        }
    }

    /** 404. Only reachable if a row went missing mid-session. */
    public static class UnknownUserException extends RuntimeException {
        public UnknownUserException(String id) {
            super("No such user: " + id);
        }
    }
}
