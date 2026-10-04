package life.simulation.engine.service;

import life.simulation.engine.domain.TerminationResult;

/**
 * A concluded board and the generation cap the walk actually used.
 *
 * @param generationsLimit after the ceiling clamp
 */
public record FinalStateOutcome(TerminationResult result, int generationsLimit) {
}
