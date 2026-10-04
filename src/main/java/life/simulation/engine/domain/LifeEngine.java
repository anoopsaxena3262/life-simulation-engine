package life.simulation.engine.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Conway rules, B3/S23, on a finite grid with dead borders.
 *
 * <p>Deliberately free of Spring, JDBC and Jackson imports so the rules can be
 * exercised in microseconds with no application context. See DESIGN.md section 4.
 *
 * <p>Note the topology: out-of-bounds neighbours count as dead, they do NOT wrap.
 * A glider therefore reaches the edge and decays rather than travelling forever.
 */
public final class LifeEngine {

    private static final Logger log = LoggerFactory.getLogger(LifeEngine.class);

    private LifeEngine() {
    }

    /**
     * Advances a flat state by exactly one generation.
     *
     * @param state  flat row-major '0'/'1' string of length {@code width * height}
     * @return the next generation in the same encoding
     */
    public static String step(String state, int width, int height) {
        log.debug("step width={} height={} stateLength={}", width, height, state == null ? null : state.length());
        boolean[][] grid = StateCodec.deserialize(state, width, height);
        boolean[][] next = step(grid, width, height);
        return StateCodec.serialize(next);
    }

    /**
     * Advances a grid by one generation into a fresh array (double buffering --
     * never mutate the input in place, the caller may still be holding it).
     */
    static boolean[][] step(boolean[][] current, int width, int height) {
        // B3/S23, written into a new grid so the caller can keep `current`.
        //   live and 2 or 3 neighbours -> live
        //   dead and exactly 3         -> live
        //   anything else              -> dead
        boolean[][] next = new boolean[height][width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int liveNeighbours = countLiveNeighbours(current, row, col, width, height);
                boolean isAlive = current[row][col];
                if (isAlive && (liveNeighbours == 2 || liveNeighbours == 3)) {
                    next[row][col] = true;
                } else if (!isAlive && liveNeighbours == 3) {
                    next[row][col] = true;
                } else {
                    next[row][col] = false;
                }
            }
        }
        return next;
    }

    /**
     * Counts live cells among the eight neighbours of {@code (row, col)}.
     * Positions outside the grid count as dead.
     */
    static int countLiveNeighbours(boolean[][] grid, int row, int col, int width, int height) {
        // TRACE, not DEBUG: this runs once per cell. DEBUG on a large board would be unreadable.
        log.trace("countLiveNeighbours row={} col={}", row, col);
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nr = row + dr;
                int nc = col + dc;
                // Off the board counts as dead. The grid does not wrap.
                if (nr >= 0 && nr < height && nc >= 0 && nc < width && grid[nr][nc]) {
                    count++;
                }
            }
        }
        return count;
    }
}
