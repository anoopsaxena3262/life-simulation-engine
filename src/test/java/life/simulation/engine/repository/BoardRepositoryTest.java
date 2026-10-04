package life.simulation.engine.repository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import life.simulation.engine.domain.Board;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a temporary-file SQLite database, not an in-memory one -- the file is
 * what the durability requirement is actually about.
 */
class BoardRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @TempDir
    Path tempDir;

    private SqliteBoardRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + tempDir.resolve("boards.db");
        dataSource.setUrl(url);
        JdbcClient jdbc = JdbcClient.create(dataSource);
        new SchemaInitializer(jdbc, url).initialise();
        repository = new SqliteBoardRepository(jdbc);
    }

    @Test
    @DisplayName("saves and reads back a board")
    void savesAndFindsBoard() {
        Board withLimit = board(40);
        Board withoutLimit = board(null);

        repository.save(withLimit);
        repository.save(withoutLimit);

        assertThat(repository.findById(withLimit.id())).contains(withLimit);
        assertThat(repository.findById(withoutLimit.id())).contains(withoutLimit);
        assertThat(repository.findGeneration(withLimit.id(), 0)).contains(withLimit.initialState());
    }

    @Test
    @DisplayName("returns empty for an unknown board id")
    void findByIdReturnsEmptyWhenAbsent() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("caches and retrieves a generation by index")
    void savesAndFindsGeneration() {
        Board board = board(null);
        repository.save(board);

        repository.saveGeneration(board.id(), 2, "111000111");

        assertThat(repository.findGeneration(board.id(), 2)).contains("111000111");
        assertThat(repository.findGeneration(board.id(), 9)).isEmpty();
        assertThat(repository.findHighestCachedIndex(board.id())).contains(2);
    }

    @Test
    @DisplayName("saving the same generation twice is idempotent")
    void saveGenerationIsIdempotent() {
        Board board = board(null);
        repository.save(board);

        repository.saveGeneration(board.id(), 1, "111000111");
        repository.saveGeneration(board.id(), 1, "000111000");

        assertThat(repository.findGeneration(board.id(), 1)).contains("111000111");
    }

    @Test
    @DisplayName("highest cached index is empty for a board with no computed generations")
    void highestCachedIndexEmptyInitially() {
        Board board = board(null);
        repository.save(board);

        assertThat(repository.findHighestCachedIndex(board.id())).isEmpty();
        assertThat(repository.findHighestCachedIndex(UUID.randomUUID())).isEmpty();
    }

    private static Board board(Integer maxGenerations) {
        return new Board(UUID.randomUUID(), 3, 3, "000111000", CREATED_AT, maxGenerations);
    }
}
