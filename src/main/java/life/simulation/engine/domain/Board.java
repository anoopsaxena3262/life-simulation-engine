package life.simulation.engine.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded board. Immutable once created: generation 0 is never overwritten,
 * and advancing the simulation produces new states rather than mutating this record.
 *
 * @param initialState generation 0 as a flat row-major '0'/'1' string of length width * height
 */
public record Board(
        UUID id,
        int width,
        int height,
        String initialState,
        Instant createdAt,
        Integer maxGenerations) {

    public int cellCount() {
        return width * height;
    }
}
