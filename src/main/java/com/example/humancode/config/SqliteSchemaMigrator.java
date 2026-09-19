package com.example.humancode.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Repairs SQLite constraints that Hibernate's update mode cannot alter. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SqliteSchemaMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!telemetryNeedsSubmitValue(connection)) {
                return;
            }

            try {
                migrateTelemetryEvents(connection);
            } catch (SQLException e) {
                if (isReadOnly(e)) {
                    log.warn("Skipping telemetry schema migration because this database is read-only");
                    return;
                }
                throw e;
            }
        }
    }

    private void migrateTelemetryEvents(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE telemetry_events_next (
                            id integer,
                            at timestamp not null,
                            deleted bigint not null,
                            detail varchar(255),
                            inserted bigint not null,
                            session_id varchar(255) not null,
                            type varchar(255) not null check
                                (type in ('EDIT','PASTE','SUBMIT','FOCUS','BLUR')),
                            primary key (id)
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO telemetry_events_next
                            (id, at, deleted, detail, inserted, session_id, type)
                        SELECT id, at, deleted, detail, inserted, session_id,
                            CASE WHEN type = 'RUN' THEN 'SUBMIT' ELSE type END
                        FROM telemetry_events
                        """);
                statement.executeUpdate("DROP TABLE telemetry_events");
                statement.executeUpdate("ALTER TABLE telemetry_events_next RENAME TO telemetry_events");
                statement.executeUpdate("CREATE INDEX idx_event_session ON telemetry_events (session_id, at)");
            connection.commit();
            log.info("Migrated telemetry_events from legacy RUN values to SUBMIT");
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private boolean isReadOnly(SQLException error) {
        return error.getMessage() != null && error.getMessage().contains("SQLITE_READONLY");
    }

    private boolean telemetryNeedsSubmitValue(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT sql FROM sqlite_master
                WHERE type = 'table' AND name = 'telemetry_events'
                """); ResultSet result = query.executeQuery()) {
            return result.next() && !result.getString(1).contains("'SUBMIT'");
        }
    }
}
