package com.example.humancode.telemetry;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryEventRepository extends JpaRepository<TelemetryEvent, Long> {

    List<TelemetryEvent> findBySessionIdOrderByAtAsc(String sessionId);
}
