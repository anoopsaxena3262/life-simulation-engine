package life.simulation.engine.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardServiceTest {

    @Test
    @DisplayName("rejects a grid whose dimensions do not match the declared width and height")
    void rejectsDimensionMismatch() {
        // TODO
    }

    @Test
    @DisplayName("rejects a board exceeding the configured cell cap")
    void rejectsOversizedBoard() {
        // TODO
    }

    @Test
    @DisplayName("generation 0 returns the uploaded board unchanged")
    void generationZeroIsTheUploadedBoard() {
        // TODO
    }

    @Test
    @DisplayName("repeated reads of the same generation return the same state")
    void readsAreIdempotent() {
        // TODO: the test that would have caught a mutating-read design --
        // TODO: call generationAt(id, 1) twice and assert both results are equal.
    }

    @Test
    @DisplayName("a cached generation is served without recomputation")
    void servesFromCache() {
        // TODO: mock the repository and verify no extra stepping occurred.
    }

    @Test
    @DisplayName("resumes from the highest cached generation rather than from zero")
    void resumesFromCache() {
        // TODO
    }

    @Test
    @DisplayName("clamps a caller-supplied generation limit to the configured ceiling")
    void clampsRequestedLimit() {
        // TODO
    }
}
