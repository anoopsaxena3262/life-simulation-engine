package life.simulation.engine.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks a board forward until it concludes, or until a generation limit is reached.
 *
 * <p>A board concludes on a fixed point (the next generation is identical) or on a
 * cycle (any previously seen generation recurs). Oscillators such as the blinker
 * never go still, so stillness alone would misclassify them. See DESIGN.md section 4.3.
 */
public final class TerminationDetector {

    private static final Logger log = LoggerFactory.getLogger(TerminationDetector.class);
    private static final long FNV_OFFSET_A = 0xcbf29ce484222325L;
    private static final long FNV_OFFSET_B = 0x6c62272e07bb0142L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /**
     * How often the encoded grid is kept so a cycle can be confirmed without
     * replaying from generation 0. On a 300×300 board each checkpoint is 90,000
     * bytes, and a walk to the generation ceiling keeps about 40 of them.
     */
    static final int CHECKPOINT_INTERVAL = 256;

    private TerminationDetector() {
    }

    /**
     * @param initialState   generation 0, flat encoded
     * @param maxGenerations how many generations to walk before giving up
     * @return the termination outcome, or empty if no conclusion was reached in time
     */
    public static Optional<TerminationResult> detect(
            String initialState, int width, int height, int maxGenerations) {
        return detect(initialState, width, height, maxGenerations, TerminationDetector::fingerprint);
    }

    /**
     * Same walk as {@link #detect(String, int, int, int)}. The fingerprint is replaceable
     * so a test can force every state onto one hash and prove a collision is not a cycle.
     */
    static Optional<TerminationResult> detect(
            String initialState, int width, int height, int maxGenerations, Fingerprint fingerprint) {
        return detect(initialState, width, height, maxGenerations, fingerprint, CHECKPOINT_INTERVAL);
    }

    static Optional<TerminationResult> detect(
            String initialState, int width, int height, int maxGenerations, int checkpointInterval) {
        return detect(initialState, width, height, maxGenerations,
                TerminationDetector::fingerprint, checkpointInterval);
    }

    /**
     * Same walk as {@link #detect(String, int, int, int, Fingerprint)}.
     * {@code checkpointInterval} is how often the encoded grid is kept for confirmation.
     */
    static Optional<TerminationResult> detect(
            String initialState, int width, int height, int maxGenerations,
            Fingerprint fingerprint, int checkpointInterval) {
        log.debug("detect width={} height={} maxGenerations={} stateLength={}",
                width, height, maxGenerations, initialState == null ? null : initialState.length());

        // Seen states are keyed by a fingerprint, not the grid. /final does not write
        // rows, and keeping every grid would exhaust the heap on a 300x300 board before
        // the generation ceiling. The fingerprint is two 64-bit FNV-1a lanes over the
        // same bytes, so a matching key is only a candidate.
        // The value is the list of generations that produced this fingerprint.
        // A candidate is a cycle only when the full string matches. That string is
        // replayed from the nearest checkpoint, at most checkpointInterval - 1 steps.

        // TerminationResult constructor:
        // kind: The termination type
        // state: next (the state at detection)
        // firstOccurrence: i for fixed point, firstOccurrence for cycle
        // period: 1 for fixed point/extinct, calculated period for cycle
        // generationsComputed: i + 1 (total generations walked)

        Map<StateHash, List<Integer>> seen = new HashMap<>();
        seen.computeIfAbsent(fingerprint.of(initialState), key -> new ArrayList<>()).add(0);
        Map<Integer, String> checkpoints = new HashMap<>();
        checkpoints.put(0, initialState);

        String current = initialState;

        for (int i = 0; i < maxGenerations; i++) {
            String next = LifeEngine.step(current, width, height);
            int nextGeneration = i + 1;

            // Check for fixed point (next generation is identical)
            if (next.equals(current)) {
                // If no live cells, it's EXTINCT; otherwise FIXED_POINT
                TerminationKind kind = StateCodec.isExtinct(next)
                        ? TerminationKind.EXTINCT
                        : TerminationKind.FIXED_POINT;
                log.debug("detect concluded kind={} period=1 generations={}", kind, nextGeneration);
                return Optional.of(new TerminationResult(kind, next, i, 1, nextGeneration));
            }

            // Check for cycle. Same hash is only a candidate; confirm the grids match.
            StateHash hash = fingerprint.of(next);
            List<Integer> earlier = seen.get(hash);
            if (earlier != null) {
                for (int firstOccurrence : earlier) {
                    String previous = stateAt(checkpoints, width, height, firstOccurrence, checkpointInterval);
                    if (!next.equals(previous)) {
                        continue;
                    }
                    int period = nextGeneration - firstOccurrence;
                    log.debug("detect concluded kind=CYCLE firstOccurrence={} period={} generations={}",
                            firstOccurrence, period, nextGeneration);
                    return Optional.of(new TerminationResult(
                            TerminationKind.CYCLE, next, firstOccurrence, period, nextGeneration));
                }
            }

            // Record this state and continue
            seen.computeIfAbsent(hash, key -> new ArrayList<>()).add(nextGeneration);
            if (nextGeneration % checkpointInterval == 0) {
                checkpoints.put(nextGeneration, next);
            }
            current = next;
        }

        // No conclusion reached within maxGenerations
        log.debug("detect no conclusion within {} generations", maxGenerations);
        return Optional.empty();
    }

    /** Two 64-bit FNV-1a lanes over the same bytes. A match is checked against the full state. */
    private static StateHash fingerprint(String state) {
        long high = FNV_OFFSET_A;
        long low = FNV_OFFSET_B;
        for (int i = 0; i < state.length(); i++) {
            long cell = state.charAt(i);
            high ^= cell;
            high *= FNV_PRIME;
            low ^= cell + 0x9e3779b97f4a7c15L;
            low *= FNV_PRIME;
        }
        return new StateHash(high, low);
    }

    private static String stateAt(
            Map<Integer, String> checkpoints, int width, int height, int index, int checkpointInterval) {
        int origin = index - Math.floorMod(index, checkpointInterval);
        String state = checkpoints.get(origin);
        for (int generation = origin; generation < index; generation++) {
            state = LifeEngine.step(state, width, height);
        }
        return state;
    }

    record StateHash(long high, long low) {
    }

    @FunctionalInterface
    interface Fingerprint {
        StateHash of(String state);
    }
}

