package life.simulation.engine.web.dto;

import java.util.UUID;

import life.simulation.engine.domain.TerminationKind;

/**
 * Final state plus the metadata describing how the board concluded.
 *
 * @param period            1 for a fixed point or extinction, greater than 1 for an oscillator
 * @param generationsLimit  the cap the walk used, after the ceiling clamp
 */
public record FinalStateResponse(
        UUID id,
        int width,
        int height,
        boolean[][] cells,
        TerminationKind terminationKind,
        int firstOccurrenceGeneration,
        int period,
        int generationsComputed,
        int generationsLimit) {
}
