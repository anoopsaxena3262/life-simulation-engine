package life.simulation.engine.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateCodecTest {

    @Test
    @DisplayName("round trip preserves an arbitrary board")
    void roundTripPreservesBoard() {
        Random random = new Random(42);
        int[][] sizes = {{1, 1}, {1, 8}, {8, 1}, {3, 5}, {17, 4}, {10, 10}};
        for (int[] size : sizes) {
            int rows = size[0];
            int cols = size[1];
            boolean[][] grid = randomGrid(random, rows, cols);

            String encoded = StateCodec.serialize(grid);

            assertThat(encoded).hasSize(rows * cols).matches("[01]+");
            boolean[][] restored = StateCodec.deserialize(encoded, cols, rows);
            assertThat(restored).isDeepEqualTo(grid);
            assertThat(StateCodec.serialize(restored)).isEqualTo(encoded);
        }
    }

    @Test
    @DisplayName("serialises a horizontal blinker to 000111000")
    void serialisesKnownPattern() {
        boolean[][] horizontalBlinker = {
                {false, false, false},
                {true, true, true},
                {false, false, false}
        };

        assertThat(StateCodec.serialize(horizontalBlinker)).isEqualTo("000111000");
        assertThat(StateCodec.deserialize("000111000", 3, 3)).isDeepEqualTo(horizontalBlinker);
    }

    @Test
    @DisplayName("encodes cells row-major, left to right then top to bottom")
    void encodesRowMajor() {
        boolean[][] grid = {
                {true, false},
                {false, true}
        };

        assertThat(StateCodec.serialize(grid)).isEqualTo("1001");
        assertThat(StateCodec.deserialize("1001", 2, 2)).isDeepEqualTo(grid);
    }

    @Test
    @DisplayName("rejects a state whose length does not match width * height")
    void rejectsLengthMismatch() {
        assertThatThrownBy(() -> StateCodec.deserialize("01", 3, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.deserialize("0001110000", 3, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.deserialize("", 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a state containing a character other than 0 or 1")
    void rejectsUnexpectedCharacter() {
        assertThatThrownBy(() -> StateCodec.deserialize("2", 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.deserialize("000111002", 3, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.deserialize(" ", 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.deserialize("00011100a", 3, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a null state and non-positive dimensions")
    void rejectsInvalidDeserializeArguments() {
        assertThatThrownBy(() -> StateCodec.deserialize(null, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.deserialize("0", 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.deserialize("0", -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.deserialize("0", 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.deserialize("0", 1, -2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a null, empty, jagged, or zero-width grid")
    void rejectsInvalidGrid() {
        assertThatThrownBy(() -> StateCodec.serialize(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.serialize(new boolean[0][]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.serialize(new boolean[][] {{}}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.serialize(new boolean[][] {null}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.serialize(new boolean[][] {{true, false}, null}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateCodec.serialize(new boolean[][] {{true, false}, {true}}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isExtinct is true only when the state contains no live cell")
    void detectsExtinction() {
        assertThat(StateCodec.isExtinct("000000000")).isTrue();
        assertThat(StateCodec.isExtinct("")).isTrue();
        assertThat(StateCodec.isExtinct("000111000")).isFalse();
        assertThat(StateCodec.isExtinct("1")).isFalse();
        assertThat(StateCodec.isExtinct(StateCodec.serialize(new boolean[][] {
                {false, false},
                {false, false}
        }))).isTrue();
        assertThatThrownBy(() -> StateCodec.isExtinct(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static boolean[][] randomGrid(Random random, int rows, int cols) {
        boolean[][] grid = new boolean[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                grid[row][col] = random.nextBoolean();
            }
        }
        return grid;
    }
}
