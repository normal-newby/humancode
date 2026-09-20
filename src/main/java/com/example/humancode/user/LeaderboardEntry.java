package com.example.humancode.user;

import java.time.Instant;

/**
 * One row of the board. Deliberately the same four facts as a leaderboard row
 * on screen and no more — there is no avatar, no title and no badge to add,
 * and UI-DESIGN.md §2 would have opinions about all three.
 *
 * @param rank 1-based, shared on a tie
 * @param you  true for the row belonging to whoever asked, so the board can mark
 *             it without the client having to match handles itself
 */
public record LeaderboardEntry(
        int rank,
        String handle,
        int rating,
        int peakRating,
        int sessionsCompleted,
        Instant lastSeenAt,
        boolean you) {
}
