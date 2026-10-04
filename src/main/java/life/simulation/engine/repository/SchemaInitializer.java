package life.simulation.engine.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Creates the schema at startup. No Flyway or Liquibase: two tables with no versioned
 * history do not justify a migration tool and its build step. See DESIGN.md section 3.2.
 */
@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    // Plain JDBC is enough. We only run a few statements when the app starts.
    private final JdbcClient jdbc;
    private final String datasourceUrl;

    public SchemaInitializer(JdbcClient jdbc, @Value("${spring.datasource.url}") String datasourceUrl) {
        log.debug("SchemaInitializer url={}", datasourceUrl);
        this.jdbc = jdbc;
        this.datasourceUrl = datasourceUrl;
    }

    /**
     * Runs once after Spring builds this bean. Creates the two tables if they are not there yet.
     */
    @PostConstruct
    public void initialise() {
        log.debug("initialise");
        createDatabaseDirectory();
        // WAL mode so if the process dies, the last commit is still on disk.
        // SQLite does not check foreign keys unless we turn them on for the connection.
        // journal_mode returns the new mode, so this is a query, not an update.
        jdbc.sql("PRAGMA journal_mode=WAL").query(String.class).single();
        jdbc.sql("PRAGMA foreign_keys=ON").update();

        // board comes first. The generation table points at board.id, so the board table
        // has to exist already or the foreign key line fails.
        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS board (
                -- One row for each board the user uploads.
                -- When someone asks for the next generation we do not change this row.
                -- The new states go in the generation table.

                -- Id we make on the server, saved as text. This is the id the API gives back.
                -- The caller does not send an id.
                id TEXT PRIMARY KEY,
                -- How many columns and how many rows. This does not change after upload.
                -- initial_state must have width * height characters.
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                -- The starting grid, generation 0. One string, row by row. 1 means alive, 0 means dead.
                -- We use this same string to notice when a state comes back again (a cycle).
                -- A 3x3 blinker when it is horizontal looks like 000111000.
                initial_state TEXT NOT NULL,
                -- Time of the upload, ISO-8601 UTC text.
                -- Helps when we open the db file and want to see which board came first.
                created_at TEXT NOT NULL,
                -- Optional stored cap for /final, used only when the request omits maxGenerations.
                -- The upload API does not set this. Rows created by POST /boards store NULL.
                -- NULL means use game-of-life.max-generations from application.yml at request time.
                -- Changing that default does change /final for boards that are already stored.
                max_generations INTEGER
            )
        """).update();

        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS generation (
                -- States we already worked out, so the next read is just a lookup.
                -- After a restart the rows are still here and we do not compute them again.
                -- For the same board and the same index the state never changes.
                -- Save uses INSERT OR IGNORE, so two requests writing the same row is fine.
                -- /final does not read or write this table. It walks from board.initial_state in memory.

                -- Which board this state belongs to.
                -- If that id is not in the board table, the insert fails when foreign keys are on.
                board_id TEXT NOT NULL REFERENCES board(id),
                -- How many generations after the upload. 0 is the grid the user sent.
                -- We write 0 when we save the board, so reading generation 0 uses this table too.
                -- To continue a walk we take MAX(idx) for this board and go on from there.
                idx INTEGER NOT NULL,
                -- The grid as 0 and 1, same way as board.initial_state.
                state TEXT NOT NULL,
                -- Only one row for a board and a generation number. This is how we find a cached state.
                PRIMARY KEY (board_id, idx)
            )
        """).update();

        // IF NOT EXISTS means a second start does not fail. It also does not change a table
        // that is already there. To drop the old file first, run ./restart.sh and pick 2.
        log.info("schema initialised successfully");
    }

    /**
     * SQLite creates the database file, but not a missing parent directory.
     */
    private void createDatabaseDirectory() {
        log.debug("createDatabaseDirectory url={}", datasourceUrl);
        String prefix = "jdbc:sqlite:";
        if (!datasourceUrl.startsWith(prefix)) {
            return;
        }
        String location = datasourceUrl.substring(prefix.length());
        int query = location.indexOf('?');
        if (query >= 0) {
            location = location.substring(0, query);
        }
        Path parent = Path.of(location).getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new UncheckedIOException("could not create database directory " + parent, ex);
        }
    }
}
