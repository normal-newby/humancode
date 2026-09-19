package com.example.humancode.interview;

import java.time.Instant;

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
public class Session {

    @Id
    private String id;

    @Column(nullable = false)
    private String problemId;

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

    protected Session() {
        // JPA
    }

    public Session(String id, String problemId, String persona, String language, Instant startedAt) {
        this.id = id;
        this.problemId = problemId;
        this.persona = persona;
        this.language = language;
        this.startedAt = startedAt;
        this.phase = Phase.INTRO;
    }

    public String getId() {
        return id;
    }

    public String getProblemId() {
        return problemId;
    }

    public String getPersona() {
        return persona;
    }

    public String getLanguage() {
        return language;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public int getImpatience() {
        return impatience;
    }

    public void setImpatience(int impatience) {
        this.impatience = impatience;
    }

    public String getFinalCode() {
        return finalCode;
    }

    public void setFinalCode(String finalCode) {
        this.finalCode = finalCode;
    }
}
