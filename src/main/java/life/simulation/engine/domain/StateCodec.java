package life.simulation.engine.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts between a boolean grid and the flat row-major '0'/'1' string used for
 * storage and as the cycle-detection key. See DESIGN.md section 3.3.
 *
 * <p>A 3x3 blinker in its horizontal phase is {@code "000111000"}.
 *
 * <p>No framework dependencies: this class is unit-testable without a Spring context.
 */
public final class StateCodec {

    private static final Logger log = LoggerFactory.getLogger(StateCodec.class);

    private StateCodec() {
    }

    /**
     * Flattens a grid to its string form, read row-major.
     *
     * @param grid indexed {@code [row][column]}
     */
    public static String serialize(boolean[][] grid) {
        log.debug("serialize rows={}", grid == null ? null : grid.length);
        if (grid == null) {
            throw new IllegalArgumentException("Grid cannot be null");
        }
        int rows = grid.length;
        if (rows == 0) {
            throw new IllegalArgumentException("Grid cannot be empty");
        }
        if (grid[0] == null) {
            throw new IllegalArgumentException("Row 0 is null");
        }
        int cols = grid[0].length;
        if (cols == 0) {
            throw new IllegalArgumentException("Grid cannot have zero columns");
        }
        // A jagged grid cannot be stored as one flat string of width * height.
        for (int i = 0; i < rows; i++) {
            if (grid[i] == null) {
                throw new IllegalArgumentException("Row " + i + " is null");
            }
            if (grid[i].length != cols) {
                throw new IllegalArgumentException(
                        "Row " + i + " has length " + grid[i].length + " but expected " + cols);
            }
        }

        StringBuilder sb = new StringBuilder(rows * cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                sb.append(grid[i][j] ? '1' : '0');
            }
        }
        return sb.toString();
    }

    /**
     * Expands a flat state back into a grid.
     *
     * @throws IllegalArgumentException if {@code state.length() != width * height},
     *                                  or if it contains a character other than '0' or '1'
     */
    public static boolean[][] deserialize(String state, int width, int height) {
        log.debug("deserialize width={} height={} stateLength={}",
                width, height, state == null ? null : state.length());
        if (state == null) {
            throw new IllegalArgumentException("State cannot be null");
        }
        if (width <= 0) {
            throw new IllegalArgumentException("Width must be positive, got: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Height must be positive, got: " + height);
        }
        // Check the length before any charAt. A short string must be a 400 from the
        // caller, not a StringIndexOutOfBoundsException that becomes a 500.
        int expectedLength = width * height;
        if (state.length() != expectedLength) {
            throw new IllegalArgumentException(
                    "State length " + state.length() + " does not match width * height (" + expectedLength + ")");
        }
        for (int i = 0; i < state.length(); i++) {
            char c = state.charAt(i);
            if (c != '0' && c != '1') {
                throw new IllegalArgumentException(
                        "State contains invalid character '" + c + "' at position " + i
                                + ", only '0' and '1' are allowed");
            }
        }

        boolean[][] grid = new boolean[height][width];
        int index = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                grid[row][col] = state.charAt(index++) == '1';
            }
        }
        return grid;
    }

    /**
     * True when no cell in the state is alive.
     */
    public static boolean isExtinct(String state) {
        log.debug("isExtinct stateLength={}", state == null ? null : state.length());
        if (state == null) {
            throw new IllegalArgumentException("State cannot be null");
        }
        // '1' is the only live marker. Absence of it means every cell is dead.
        return !state.contains("1");
    }
}
