package com.example.gameoflife.domain;

/**
 * Walks a board forward until it concludes, or until a generation limit is reached.
 *
 * <p>A board concludes on a fixed point (the next generation is identical) or on a
 * cycle (any previously seen generation recurs). Oscillators such as the blinker
 * never go still, so stillness alone would misclassify them. See DESIGN.md section 4.3.
 */
public final class TerminationDetector {

    private TerminationDetector() {
    }

    /**
     * @param initialState  generation 0, flat encoded
     * @param maxGenerations how many generations to walk before giving up
     * @return the termination outcome, or empty if no conclusion was reached in time
     */
    public static java.util.Optional<TerminationResult> detect(
            String initialState, int width, int height, int maxGenerations) {

        // TODO: seen = new HashMap<String, Integer>(), put(initialState, 0).
        // TODO: loop i from 0 while i < maxGenerations:
        // TODO:   next = LifeEngine.step(current, width, height)
        // TODO:   if next.equals(current)      -> FIXED_POINT (or EXTINCT when no live cell), period 1
        // TODO:   if seen.containsKey(next)    -> CYCLE, firstOccurrence = seen.get(next),
        // TODO:                                   period = (i + 1) - firstOccurrence
        // TODO:   seen.put(next, i + 1); current = next
        // TODO: fall through -> Optional.empty(), the caller turns this into 422.
        //
        // TODO: memory is bounded by maxGenerations, which is why the map is preferred
        // TODO: over Floyd/Brent -- it yields entry point and period directly.
        throw new UnsupportedOperationException("not implemented");
    }
}
