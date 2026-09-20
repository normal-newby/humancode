package com.example.humancode.user;

/**
 * Everything about a candidate that is safe to put on the wire.
 *
 * <p>Notably not here: {@link User#getToken()}. It is returned exactly once, by
 * the claim that minted it, and never again — a profile is fetched by handle
 * and a profile that carried the secret would hand every account to whoever
 * asked for it by name.
 *
 * @param rank 1-based, ties shared, or {@code 0} for a candidate who has not
 *             finished a session yet — unranked rather than last.
 */
public record UserProfile(
        String handle,
        int rating,
        int peakRating,
        int sessionsCompleted,
        int rank) {

    public static UserProfile of(User user, int rank) {
        return new UserProfile(
                user.getHandle(),
                user.getRating(),
                user.getPeakRating(),
                user.getSessionsCompleted(),
                rank);
    }
}
