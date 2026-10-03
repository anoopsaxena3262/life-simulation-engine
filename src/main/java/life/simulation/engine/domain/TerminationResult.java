package life.simulation.engine.domain;

/**
 * Outcome of walking a board forward until it concludes.
 *
 * @param kind                how the board concluded
 * @param state               the state at the point of detection
 * @param firstOccurrence     generation index where this state was first seen
 * @param period              1 for a fixed point or extinction, &gt;1 for a cycle
 * @param generationsComputed how many generations were walked to get here
 */
public record TerminationResult(
        TerminationKind kind,
        String state,
        int firstOccurrence,
        int period,
        int generationsComputed) {
}
