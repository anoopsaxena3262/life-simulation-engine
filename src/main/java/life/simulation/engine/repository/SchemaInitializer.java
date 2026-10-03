package life.simulation.engine.repository;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Creates the schema at startup. No Flyway or Liquibase: two tables with no versioned
 * history do not justify a migration tool and its build step. See DESIGN.md section 3.2.
 */
@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final JdbcClient jdbc;

    public SchemaInitializer(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initialise() {
        // TODO: PRAGMA journal_mode=WAL  -- durability across an abrupt process kill.
        // TODO: PRAGMA foreign_keys=ON   -- off by default in SQLite.
        //
        // TODO: CREATE TABLE IF NOT EXISTS board (
        // TODO:   id TEXT PRIMARY KEY, width INTEGER NOT NULL, height INTEGER NOT NULL,
        // TODO:   initial_state TEXT NOT NULL, created_at TEXT NOT NULL,
        // TODO:   max_generations INTEGER )
        //
        // TODO: CREATE TABLE IF NOT EXISTS generation (
        // TODO:   board_id TEXT NOT NULL REFERENCES board(id),
        // TODO:   idx INTEGER NOT NULL, state TEXT NOT NULL,
        // TODO:   PRIMARY KEY (board_id, idx) )
        log.info("schema initialisation not yet implemented");
    }
}
