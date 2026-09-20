package com.example.humancode.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The account and the board, against the real SQLite schema.
 *
 * <p>{@code dev} for the same reason every other {@code @SpringBootTest} here
 * uses it (CLAUDE.md §6): a context publishes {@code ApplicationReadyEvent} and
 * would otherwise warm the problem pool — two model calls and a minute of
 * latency on every build.
 *
 * <p>Handles are suffixed with a random chunk and deleted afterwards, because
 * this runs against the developer's own database rather than an in-memory one,
 * and a leftover {@code nimo} would fail the next run on a taken handle.
 */
@SpringBootTest
@ActiveProfiles("dev")
class UserServiceTest {

    @Autowired
    private UserService users;

    @Autowired
    private UserRepository repository;

    private final List<String> claimed = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        claimed.forEach(id -> repository.deleteById(id));
        claimed.clear();
    }

    private User claim(String prefix) {
        User user = users.claim(prefix + "-" + UUID.randomUUID().toString().substring(0, 6));
        claimed.add(user.getId());
        return user;
    }

    @Test
    void claimingMintsATokenAndStartsAtZero() {
        User user = claim("fresh");
        assertFalse(user.getToken().isBlank());
        assertEquals(0, user.getRating());
        assertEquals(0, user.getSessionsCompleted());
        // Unranked, not last: nothing has judged them yet.
        assertEquals(0, users.profile(user).rank());
    }

    @Test
    void theSameHandleCannotBeClaimedTwice() {
        User user = claim("dup");
        assertThrows(UserService.HandleTakenException.class, () -> users.claim(user.getHandle()));
        // And the case-folded spelling is the same handle, not a second one.
        assertThrows(UserService.HandleTakenException.class,
                () -> users.claim(user.getHandle().toUpperCase()));
    }

    @Test
    void resumeNeedsTheRightToken() {
        User user = claim("resume");
        assertEquals(user.getId(), users.resume(user.getHandle(), user.getToken()).getId());
        assertThrows(UserService.UnauthorizedException.class,
                () -> users.resume(user.getHandle(), "not-the-token"));
        assertThrows(UserService.UnauthorizedException.class,
                () -> users.resume("nobody-" + UUID.randomUUID(), user.getToken()));
    }

    @Test
    void verifyIsQuietWhereResumeThrows() {
        User user = claim("verify");
        assertTrue(users.verify(user.getHandle(), user.getToken()).isPresent());
        // The session-start path: a stale token means anonymous, not an error.
        assertTrue(users.verify(user.getHandle(), "stale").isEmpty());
        assertTrue(users.verify(null, null).isEmpty());
    }

    @Test
    void ratingAccumulatesAndPeakSurvivesABadSession() {
        User user = claim("peak");
        users.recordSession(user.getId(), 24);
        UserProfile after = users.recordSession(user.getId(), -15);

        assertEquals(9, after.rating());
        assertEquals(24, after.peakRating());
        assertEquals(2, after.sessionsCompleted());
    }

    @Test
    void ratingIsAllowedToGoNegative() {
        // The corner of every screen renders a minus sign in --color-hot, and
        // it is supposed to be reachable. A floor at zero would quietly make
        // the worst session of the night identical to never having played.
        User user = claim("negative");
        UserProfile after = users.recordSession(user.getId(), -15);
        assertEquals(-15, after.rating());
    }

    @Test
    void tiesShareARankAndTheNextDistinctScoreSkipsThem() {
        User top = claim("btop");
        User tieA = claim("btiea");
        User tieB = claim("btieb");
        User below = claim("blow");

        users.recordSession(top.getId(), 30);
        users.recordSession(tieA.getId(), 20);
        users.recordSession(tieB.getId(), 20);
        users.recordSession(below.getId(), 10);

        // Everything else in the database may outrank these four, so assert on
        // the shape of their own run rather than on absolute positions.
        List<LeaderboardEntry> board = users.leaderboard(100, tieA.getHandle());
        int first = indexOf(board, top.getHandle());
        int a = indexOf(board, tieA.getHandle());
        int b = indexOf(board, tieB.getHandle());
        int last = indexOf(board, below.getHandle());

        assertTrue(first < a && a < b && b < last, "ordered by rating, descending");
        assertEquals(board.get(a).rank(), board.get(b).rank(), "a tie shares one rank");
        assertEquals(board.get(first).rank() + 1, board.get(a).rank(), "no gap before a tie");
        // Two candidates shared rank 2, so the next distinct rating is 4, not 3.
        assertEquals(board.get(a).rank() + 2, board.get(last).rank(), "the tie uses up two places");
    }

    @Test
    void theBoardMarksTheViewersOwnRow() {
        User mine = claim("mine");
        User theirs = claim("theirs");
        users.recordSession(mine.getId(), 5);
        users.recordSession(theirs.getId(), 5);

        List<LeaderboardEntry> board = users.leaderboard(100, mine.getHandle().toUpperCase());
        assertTrue(board.get(indexOf(board, mine.getHandle())).you());
        assertFalse(board.get(indexOf(board, theirs.getHandle())).you());
    }

    @Test
    void anUnjudgedHandleIsNotOnTheBoardAtAll() {
        User lurker = claim("lurker");
        List<LeaderboardEntry> board = users.leaderboard(100, null);
        assertEquals(-1, indexOf(board, lurker.getHandle()));
    }

    private int indexOf(List<LeaderboardEntry> board, String handle) {
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).handle().equals(handle)) {
                return i;
            }
        }
        return -1;
    }
}
