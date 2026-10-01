package com.example.gameoflife.domain;

/**
 * Converts between a boolean grid and the flat row-major '0'/'1' string used for
 * storage and as the cycle-detection key. See DESIGN.md section 3.3.
 *
 * <p>A 3x3 blinker in its horizontal phase is {@code "000111000"}.
 *
 * <p>No framework dependencies: this class is unit-testable without a Spring context.
 */
public final class StateCodec {

    private StateCodec() {
    }

    /**
     * Flattens a grid to its string form, read row-major.
     *
     * @param grid indexed {@code [row][column]}
     */
    public static String serialize(boolean[][] grid) {
        // TODO: walk rows then columns, appending '1' for live and '0' for dead.
        // TODO: use a StringBuilder sized to rows * columns.
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Expands a flat state back into a grid.
     *
     * @throws IllegalArgumentException if {@code state.length() != width * height},
     *                                  or if it contains a character other than '0' or '1'
     */
    public static boolean[][] deserialize(String state, int width, int height) {
        // TODO: validate length equals width * height BEFORE indexing into the string --
        // TODO: a blind charAt here turns a malformed request into a 500.
        // TODO: reject any character that is not '0' or '1'.
        // TODO: fill boolean[height][width] row-major.
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * True when no cell in the state is alive.
     */
    public static boolean isExtinct(String state) {
        // TODO: no '1' anywhere in the string.
        throw new UnsupportedOperationException("not implemented");
    }
}
