package life.simulation.engine.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The direct test of the durability requirement, and the single most important test
 * in this project: the brief asks that the service survive a restart and retain board
 * state. Everything else is secondary to this one passing.
 */
class RestartPersistenceTest {

    @Test
    @DisplayName("board and cached generations survive an application context restart")
    void stateSurvivesContextRestart() {
        // TODO: start a context against a temp database file.
        // TODO: POST a board, read a few generations so the cache is populated, record the id.
        // TODO: close the context.
        // TODO: start a NEW context against the SAME file.
        // TODO: assert the board is still readable and the cached generations are intact.
    }
}
