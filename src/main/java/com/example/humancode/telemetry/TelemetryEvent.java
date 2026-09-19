package com.example.humancode.telemetry;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * The append-only replay log. Every edit the candidate makes lands here, which
 * is what makes the report card's session replay possible for free.
 *
 * <p>Keep this cheap to write — nothing here should ever be updated.
 */
@Entity
@Table(name = "telemetry_events", indexes = @Index(name = "idx_event_session", columnList = "sessionId,at"))
public class TelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Column(nullable = false)
    private Instant at;

    private long inserted;

    private long deleted;

    /** Free-form detail: pasted length, test summary, etc. */
    private String detail;

    protected TelemetryEvent() {
        // JPA
    }

    public TelemetryEvent(String sessionId, EventType type, Instant at, long inserted, long deleted, String detail) {
        this.sessionId = sessionId;
        this.type = type;
        this.at = at;
        this.inserted = inserted;
        this.deleted = deleted;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public EventType getType() {
        return type;
    }

    public Instant getAt() {
        return at;
    }

    public long getInserted() {
        return inserted;
    }

    public long getDeleted() {
        return deleted;
    }

    public String getDetail() {
        return detail;
    }
}
