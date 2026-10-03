package life.simulation.engine.domain;

/**
 * How a board concluded. See DESIGN.md section 4.3.
 */
public enum TerminationKind {

    /** Every cell is dead. Reported separately from FIXED_POINT for clearer semantics. */
    EXTINCT,

    /** The next generation is identical to the current one (period 1). */
    FIXED_POINT,

    /** A previously seen generation recurred, with period greater than 1. */
    CYCLE
}
