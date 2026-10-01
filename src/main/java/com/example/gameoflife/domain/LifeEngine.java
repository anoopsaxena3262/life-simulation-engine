package com.example.gameoflife.domain;

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
        // TODO: deserialize, step the grid, serialize the result.
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Advances a grid by one generation into a fresh array (double buffering --
     * never mutate the input in place, the caller may still be holding it).
     */
    static boolean[][] step(boolean[][] current, int width, int height) {
        // TODO: allocate boolean[height][width] for the next generation.
        // TODO: for each cell, count live neighbours, then apply:
        // TODO:   live  && (n == 2 || n == 3) -> live
        // TODO:   dead  && n == 3             -> live
        // TODO:   otherwise                   -> dead
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Counts live cells among the eight neighbours of {@code (row, col)}.
     * Positions outside the grid count as dead.
     */
    static int countLiveNeighbours(boolean[][] grid, int row, int col, int width, int height) {
        // TODO: iterate dr in -1..1, dc in -1..1, skipping (0,0).
        // TODO: bounds-check before reading; out of range contributes nothing.
        throw new UnsupportedOperationException("not implemented");
    }
}
