package com.example.humancode.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByHandle(String handle);

    boolean existsByHandle(String handle);

    /**
     * The board, and the order is spelled out here rather than left to callers:
     * rating first, then <em>fewer</em> sessions, so two candidates on the same
     * number are separated by who needed less of the evening to get there.
     * Oldest account breaks a full tie — arbitrary, but stable, and a board
     * that reshuffles equal rows on every poll looks broken.
     *
     * <p>{@code sessionsCompleted > 0} is the filter that keeps it a
     * leaderboard rather than a sign-up sheet: claiming a handle and walking
     * away puts you nowhere, because you have not been judged yet.
     */
    List<User> findBySessionsCompletedGreaterThanOrderByRatingDescSessionsCompletedAscCreatedAtAsc(
            int minSessions, Pageable page);

    /** Competition ranking: everyone on the same rating shares a rank. */
    long countBySessionsCompletedGreaterThanAndRatingGreaterThan(int minSessions, int rating);

    long countBySessionsCompletedGreaterThan(int minSessions);
}
