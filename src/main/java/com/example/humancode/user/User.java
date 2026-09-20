package com.example.humancode.user;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A candidate, across sessions.
 *
 * <p>The whole account is a handle and a secret. There is no email, no password
 * and nothing to reset: the candidate claims a handle, the browser keeps the
 * token that came back, and that pair is what lets a rating survive the tab
 * being closed. That is a deliberate ceiling, not an unfinished auth system —
 * a real credential form on this screen would read as the thing UI-DESIGN.md §0
 * refuses to build, and a leaderboard at a demo table does not need one.
 *
 * <p>What it costs: whoever holds the token is the candidate, and losing it
 * loses the handle. {@link UserService} is the only thing that ever compares
 * one, and it never leaves the server except in the response to the claim that
 * created it.
 */
@Entity
@Table(name = "users", indexes = @Index(name = "idx_user_rating", columnList = "rating"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA
public class User {

    @Id
    private String id;

    /** Normalized by {@link Handles#require} before it ever gets here. */
    @Column(nullable = false, unique = true)
    private String handle;

    /** The browser's proof that this handle is theirs. Never serialized. */
    @Column(nullable = false)
    private String token;

    /**
     * The running total of every finished session's {@code ratingDelta}. It is
     * allowed to go negative, and it is supposed to — a candidate who has been
     * shipping broken apps all evening has earned the minus sign, and the
     * corner of every screen already renders one in {@code --color-hot}.
     */
    private int rating;

    /** The best it has ever been, which a bad run cannot take away. */
    private int peakRating;

    /** Sessions actually finished — a session abandoned mid-way never lands here. */
    private int sessionsCompleted;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    public User(String id, String handle, String token, Instant now) {
        this.id = id;
        this.handle = handle;
        this.token = token;
        this.createdAt = now;
        this.lastSeenAt = now;
    }
}
