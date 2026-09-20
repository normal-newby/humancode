package com.example.humancode.interview;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** The durable record of a session. Written at phase transitions and at the end. */
@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA
public class Session {

    @Id
    private String id;

    @Column(nullable = false)
    private String problemId;

    /**
     * The candidate this session belongs to, or null when nobody was signed in.
     * Nullable on purpose — a handle is optional (see {@code user/User}), and an
     * anonymous session is a perfectly ordinary one that simply moves no
     * leaderboard row.
     */
    private String userId;

    /**
     * Compatibility value for databases created before personas were removed
     * from the public session API. The legacy SQLite column is still NOT NULL.
     */
    @Column(nullable = false)
    private String persona;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Phase phase;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    private int impatience;

    @Lob
    private String finalCode;

    public Session(String id, String problemId, String language, Instant startedAt) {
        this.id = id;
        this.problemId = problemId;
        this.persona = "senior-engineer";
        this.language = language;
        this.startedAt = startedAt;
        this.phase = Phase.INTRO;
    }
}
