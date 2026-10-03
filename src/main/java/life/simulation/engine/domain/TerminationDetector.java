package life.simulation.engine.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
     * @param initialState   generation 0, flat encoded
     * @param maxGenerations how many generations to walk before giving up
     * @return the termination outcome, or empty if no conclusion was reached in time
     */
    public static Optional<TerminationResult> detect(
            String initialState, int width, int height, int maxGenerations) {

        // HashMap for state tracking and cycle detection.
        // Key is state string, value is generation index of first occurrence.
        // Initialize with the initial state at generation 0.

        // TerminationResult constructor:
        // kind: The termination type
        // state: next (the state at detection)
        // firstOccurrence: i for fixed point, firstOccurrence for cycle
        // period: 1 for fixed point/extinct, calculated period for cycle
        // generationsComputed: i + 1 (total generations walked)

        Map<String, Integer> seen = new HashMap<>();
        seen.put(initialState, 0);

        String current = initialState;

        for (int i = 0; i < maxGenerations; i++) {
            String next = LifeEngine.step(current, width, height);

            // Check for fixed point (next generation is identical)
            if (next.equals(current)) {
                // If no live cells, it's EXTINCT; otherwise FIXED_POINT
                TerminationKind kind = StateCodec.isExtinct(next)
                        ? TerminationKind.EXTINCT
                        : TerminationKind.FIXED_POINT;
                return Optional.of(new TerminationResult(kind, next, i, 1, i + 1));
            }

            // Check for cycle (we've seen this state before)
            Integer firstOccurrence = seen.get(next);
            if (firstOccurrence != null) {
                int period = (i + 1) - firstOccurrence;
                return Optional.of(new TerminationResult(
                        TerminationKind.CYCLE, next, firstOccurrence, period, i + 1));
            }

            // Record this state and continue
            seen.put(next, i + 1);
            current = next;
        }

        // No conclusion reached within maxGenerations
        return Optional.empty();
    }
}

