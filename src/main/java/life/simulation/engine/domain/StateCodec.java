package life.simulation.engine.domain;

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
        // Walk rows then columns, appending '1' for live and '0' for dead.
        // StringBuilder sized to rows * columns.

        // Validate the grid is not null and we can add at API level as well. 

        if (grid == null) {
            throw new IllegalArgumentException("Grid cannot be null");
        }

    // Some more defesive programming length checks to ensure the grid is not empty .
    

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
    
    // Check for jagged array
    for (int i = 0; i < rows; i++) {
        if (grid[i] == null) {
            throw new IllegalArgumentException("Row " + i + " is null");
        }
        if (grid[i].length != cols) {
            throw new IllegalArgumentException(
                "Row " + i + " has length " + grid[i].length + " but expected " + cols);
        }
    }


        // Get the number of rows and columns
       
        StringBuilder sb = new StringBuilder(rows * cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                sb.append(grid[i][j] ? '1' : '0');
            }

        }
        // Return the StringBuilder as a string.


        return sb.toString();

    }

    /**
     * Expands a flat state back into a grid.
     *
     * @throws IllegalArgumentException if {@code state.length() != width * height},
     *                                  or if it contains a character other than '0' or '1'
     */
    public static boolean[][] deserialize(String state, int width, int height) {
        // Validates length equals width * height BEFORE indexing into the string --
        // blind charAt here turns a malformed request into a 500.
        // reject any character that is not '0' or '1'.
        // fill boolean[height][width] row-major.

// Validate state is not null
    if (state == null) {
        throw new IllegalArgumentException("State cannot be null");
    }
    
    // Validate width and height are positive
    if (width <= 0) {
        throw new IllegalArgumentException("Width must be positive, got: " + width);
    }
    if (height <= 0) {
        throw new IllegalArgumentException("Height must be positive, got: " + height);
    }
    
    // Validate length equals width * height BEFORE indexing into the string
    // A blind charAt here turns a malformed request into a 500
    int expectedLength = width * height;
    if (state.length() != expectedLength) {
        throw new IllegalArgumentException(
            "State length " + state.length() + " does not match width * height (" + expectedLength + ")");
    }
 
    // Reject any character that is not '0' or '1'
    for (int i = 0; i < state.length(); i++) {
        char c = state.charAt(i);
        if (c != '0' && c != '1') {
            throw new IllegalArgumentException(
                "State contains invalid character '" + c + "' at position " + i + ", only '0' and '1' are allowed");
        }
    }
 
    // Fill boolean[height][width] row-major
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
        // Validate state is not null - defensive programming. 
         if (state == null) {
        throw new IllegalArgumentException("State cannot be null");
    }
    // Since checking for just '1' used contains method to check if the state contains '1' - returns a boolean. 
    return !state.contains("1");
     }
}
