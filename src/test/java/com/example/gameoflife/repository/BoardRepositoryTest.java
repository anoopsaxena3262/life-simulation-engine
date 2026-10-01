package com.example.gameoflife.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs against a temporary-file SQLite database, not an in-memory one -- the file is
 * what the durability requirement is actually about.
 */
class BoardRepositoryTest {

    @Test
    @DisplayName("saves and reads back a board")
    void savesAndFindsBoard() {
        // TODO
    }

    @Test
    @DisplayName("returns empty for an unknown board id")
    void findByIdReturnsEmptyWhenAbsent() {
        // TODO
    }

    @Test
    @DisplayName("caches and retrieves a generation by index")
    void savesAndFindsGeneration() {
        // TODO
    }

    @Test
    @DisplayName("saving the same generation twice is idempotent")
    void saveGenerationIsIdempotent() {
        // TODO: this is the concurrent-computation race -- INSERT OR IGNORE, no exception.
    }

    @Test
    @DisplayName("highest cached index is empty for a board with no computed generations")
    void highestCachedIndexEmptyInitially() {
        // TODO
    }
}
