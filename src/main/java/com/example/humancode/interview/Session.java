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
        this.language = language;
        this.startedAt = startedAt;
        this.phase = Phase.INTRO;
    }
}
