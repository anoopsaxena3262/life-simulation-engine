package life.simulation.engine.repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import life.simulation.engine.GameOfLifeApplication;
import life.simulation.engine.domain.Board;
import life.simulation.engine.service.BoardService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The direct test of the durability requirement, and the single most important test
 * in this project: the brief asks that the service survive a restart and retain board
 * state. Everything else is secondary to this one passing.
 *
 * <p>Runs once, after the repository tests. Those already prove the SQL. This one
 * proves a second process, started the way the application starts, can read what the
 * first process committed. A failure here is lifecycle or configuration.
 */
class RestartPersistenceTest {

    /** Far enough that the cache holds more than generation 0, which save() writes itself. */
    private static final int CACHED_THROUGH = 4;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("board and cached generations survive an application context restart")
    void stateSurvivesContextRestart() {
        Path database = tempDir.resolve("boards.db");

        UUID id;
        Board uploaded;
        List<String> cached = new ArrayList<>();

        try (ConfigurableApplicationContext first = start(database)) {
            BoardService service = first.getBean(BoardService.class);
            id = service.create(3, 3, blinker());
            // Reading generation 4 writes rows 1 through 4. Generation 0 was written on save.
            service.generationAt(id, CACHED_THROUGH);

            BoardRepository repository = first.getBean(BoardRepository.class);
            uploaded = repository.findById(id).orElseThrow();
            for (int index = 0; index <= CACHED_THROUGH; index++) {
                cached.add(repository.findGeneration(id, index).orElseThrow());
            }
        }

        try (ConfigurableApplicationContext second = start(database)) {
            BoardService service = second.getBean(BoardService.class);
            BoardRepository repository = second.getBean(BoardRepository.class);

            // Read the stored rows. generationAt would recompute a missing cache and hide the loss.
            assertThat(service.get(id)).isEqualTo(uploaded);
            assertThat(repository.findHighestCachedIndex(id)).contains(CACHED_THROUGH);
            for (int index = 0; index <= CACHED_THROUGH; index++) {
                assertThat(repository.findGeneration(id, index)).contains(cached.get(index));
            }
        }
    }

    /**
     * Command-line arguments outrank application.yml, so this context uses the temp file
     * rather than data/game-of-life.db. Port 0 avoids colliding with a server already on 8080.
     */
    private static ConfigurableApplicationContext start(Path database) {
        return SpringApplication.run(
                GameOfLifeApplication.class,
                "--spring.datasource.url=jdbc:sqlite:" + database.toAbsolutePath(),
                "--server.port=0");
    }

    private static boolean[][] blinker() {
        return new boolean[][] {
                {false, false, false},
                {true, true, true},
                {false, false, false}
        };
    }
}
