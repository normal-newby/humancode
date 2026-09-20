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

/**
 * Repairs SQLite schema that Hibernate's update mode cannot alter.
 *
 * <p>Two kinds of thing land here, both of which look like application bugs
 * and are not. A {@code check (col in (...))} freezes an enum's constants at
 * the moment the table was created and neither Hibernate nor SQLite will
 * rewrite it; and Hibernate's {@code add column} DDL omits a default, so
 * SQLite refuses any new {@code NOT NULL} column on a table that already has
 * rows. Both leave a database that is a few commits old throwing at runtime
 * while a brand-new one works perfectly.
 *
 * <p><b>Each repair is written for one specific change and does not
 * generalise.</b> Renaming another enum constant, or adding another non-null
 * column, needs a new check and a new step here — see CLAUDE.md §3.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SqliteSchemaMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    /** One repair step, so the read-only handling is written once. */
    private interface Repair {
        void apply(Connection connection) throws SQLException;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (telemetryNeedsSubmitValue(connection)) {
                repair(connection, this::migrateTelemetryEvents, "telemetry_events");
            }
            if (telemetryNeedsHintValue(connection)) {
                repair(connection, this::migrateTelemetryEventsForHint, "telemetry_events (HINT)");
            }
            if (sessionsMissingPersona(connection)) {
                repair(connection, this::addPersonaColumn, "sessions");
            }
        }
    }

    private void repair(Connection connection, Repair step, String table) throws SQLException {
        try {
            step.apply(connection);
        } catch (SQLException e) {
            if (isReadOnly(e)) {
                log.warn("Skipping the {} migration because this database is read-only", table);
                return;
            }
            throw e;
        }
    }

    /**
     * Adds the column Hibernate cannot.
     *
     * <p>{@code Session.persona} exists only to keep databases that predate
     * the persona removal working, where the column is still {@code NOT NULL}.
     * A database created in between — after personas went, before the field
     * came back — has no column at all, and Hibernate's
     * {@code alter table sessions add column persona varchar(255) not null}
     * is rejected outright, because SQLite will not add a non-null column
     * without a default. Every read of a session then fails on
     * {@code no such column: persona}. Supplying the default is the whole fix.
     */
    private void addPersonaColumn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "ALTER TABLE sessions ADD COLUMN persona varchar(255) NOT NULL DEFAULT 'senior-engineer'");
        }
        log.info("Added the legacy sessions.persona column, defaulted for existing rows");
    }

    /** False when there is no sessions table yet — Hibernate will create it correctly. */
    private boolean sessionsMissingPersona(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT sql FROM sqlite_master
                WHERE type = 'table' AND name = 'sessions'
                """); ResultSet result = query.executeQuery()) {
            return result.next() && !result.getString(1).contains("persona");
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

    /**
     * Same repair as {@link #migrateTelemetryEvents}, one enum constant later:
     * {@code EventType.HINT} is new, so a database whose {@code telemetry_events}
     * table predates it has a check constraint that does not list it, and every
     * hint insert throws {@code SQLITE_CONSTRAINT_CHECK} on a database that is
     * otherwise perfectly healthy.
     */
    private void migrateTelemetryEventsForHint(Connection connection) throws SQLException {
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
                            (type in ('EDIT','PASTE','SUBMIT','FOCUS','BLUR','HINT')),
                        primary key (id)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO telemetry_events_next
                        (id, at, deleted, detail, inserted, session_id, type)
                    SELECT id, at, deleted, detail, inserted, session_id, type
                    FROM telemetry_events
                    """);
            statement.executeUpdate("DROP TABLE telemetry_events");
            statement.executeUpdate("ALTER TABLE telemetry_events_next RENAME TO telemetry_events");
            statement.executeUpdate("CREATE INDEX idx_event_session ON telemetry_events (session_id, at)");
            connection.commit();
            log.info("Migrated telemetry_events to allow HINT values");
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private boolean telemetryNeedsHintValue(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT sql FROM sqlite_master
                WHERE type = 'table' AND name = 'telemetry_events'
                """); ResultSet result = query.executeQuery()) {
            return result.next() && !result.getString(1).contains("'HINT'");
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
