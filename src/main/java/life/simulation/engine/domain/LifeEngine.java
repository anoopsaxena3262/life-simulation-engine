package life.simulation.engine.domain;

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

    private LifeEngine() {
    }

    /**
     * Advances a flat state by exactly one generation.
     *
     * @param state  flat row-major '0'/'1' string of length {@code width * height}
     * @return the next generation in the same encoding
     */
    public static String step(String state, int width, int height) {
        //  Deserialize, step the grid, serialize the result. 

        boolean[][] grid = StateCodec.deserialize(state, width, height);
        boolean[][] next = step(grid, width, height);
        return StateCodec.serialize(next);

    }

    /**
     * Advances a grid by one generation into a fresh array (double buffering --
     * never mutate the input in place, the caller may still be holding it).
     */
    static boolean[][] step(boolean[][] current, int width, int height) {
        //  Allocate boolean[height][width] for the next generation.
        //  Conway's B3/S23 rules apply to each cell:
        //  For each cell, count live neighbours, then apply:
        //      live  && (n == 2 || n == 3) -> live
        //      dead  && n == 3             -> live
        //      otherwise                   -> dead

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
        // iterate dr in -1..1, dc in -1..1, skipping (0,0).
        // bounds-check before reading; out of range contributes nothing.
        
    //  Count the live neighbours
    int count = 0;
    
    for (int dr = -1; dr <= 1; dr++) {
        for (int dc = -1; dc <= 1; dc++) {
            // Skip the cell itself
            if (dr == 0 && dc == 0) {
                continue;
            }
            
            int nr = row + dr;
            int nc = col + dc;
            
            // Bounds-check: out of range contributes nothing (dead border)
            if (nr >= 0 && nr < height && nc >= 0 && nc < width) {
                if (grid[nr][nc]) {
                    count++;
                }
            }
        }
    }
    
    return count;
    }
}
