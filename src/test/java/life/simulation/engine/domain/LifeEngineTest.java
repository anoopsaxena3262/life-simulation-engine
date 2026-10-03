package life.simulation.engine.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules, exercised without an application context. These are the tests that
 * run in microseconds, which is the point of keeping the domain framework-free.
 */
class LifeEngineTest {

    @Test
    @DisplayName("block is a still life: unchanged after a step")
    void blockIsStable() {
        String block = rows(
                "0000",
                "0110",
                "0110",
                "0000");

        assertThat(LifeEngine.step(block, 4, 4)).isEqualTo(block);
        assertThat(advance(block, 4, 4, 5)).isEqualTo(block);
    }

    @Test
    @DisplayName("blinker oscillates with period 2")
    void blinkerOscillates() {
        String horizontal = rows(
                "000",
                "111",
                "000");
        String vertical = rows(
                "010",
                "010",
                "010");

        assertThat(LifeEngine.step(horizontal, 3, 3)).isEqualTo(vertical);
        assertThat(advance(horizontal, 3, 3, 2)).isEqualTo(horizontal);
    }

    @Test
    @DisplayName("toad oscillates with period 2")
    void toadOscillates() {
        String phaseA = rows(
                "000000",
                "001110",
                "011100",
                "000000");
        String phaseB = rows(
                "000100",
                "010010",
                "010010",
                "001000");

        assertThat(LifeEngine.step(phaseA, 6, 4)).isEqualTo(phaseB);
        assertThat(advance(phaseA, 6, 4, 2)).isEqualTo(phaseA);
    }

    @Test
    @DisplayName("beacon oscillates with period 2")
    void beaconOscillates() {
        String phaseA = rows(
                "1100",
                "1100",
                "0011",
                "0011");
        String phaseB = rows(
                "1100",
                "1000",
                "0001",
                "0011");

        assertThat(LifeEngine.step(phaseA, 4, 4)).isEqualTo(phaseB);
        assertThat(advance(phaseA, 4, 4, 2)).isEqualTo(phaseA);
    }

    @Test
    @DisplayName("glider shifts one cell down and one cell across every four generations")
    void gliderTranslates() {
        String start = glider();
        String shiftedOnce = rows(
                "000000",
                "001000",
                "000100",
                "011100",
                "000000",
                "000000");
        String shiftedTwice = rows(
                "000000",
                "000000",
                "000100",
                "000010",
                "001110",
                "000000");

        assertThat(advance(start, GLIDER_SIZE, GLIDER_SIZE, 4)).isEqualTo(shiftedOnce);
        assertThat(advance(start, GLIDER_SIZE, GLIDER_SIZE, 8)).isEqualTo(shiftedTwice);
    }

    @Test
    @DisplayName("the same glider dies in the corner and does not reappear on the opposite edge")
    void gliderDiesAtBoundary() {
        // Same southeast glider. On a torus, generation 13 puts a live cell on the top edge.
        String atImpact = advance(glider(), GLIDER_SIZE, GLIDER_SIZE, 13);
        assertThat(edge(atImpact, 0)).isEqualTo("000000");
        for (int row = 0; row < GLIDER_SIZE; row++) {
            assertThat(atImpact.charAt(row * GLIDER_SIZE)).isEqualTo('0');
        }

        String cornerBlock = rows(
                "000000",
                "000000",
                "000000",
                "000000",
                "000011",
                "000011");
        String settled = advance(glider(), GLIDER_SIZE, GLIDER_SIZE, 16);
        assertThat(settled).isEqualTo(cornerBlock);
        assertThat(LifeEngine.step(settled, GLIDER_SIZE, GLIDER_SIZE)).isEqualTo(settled);
        assertThat(edge(settled, 0)).isEqualTo("000000");
    }

    @Test
    @DisplayName("empty board stays empty")
    void emptyBoardStaysEmpty() {
        String empty = rows(
                "0000",
                "0000",
                "0000",
                "0000");

        assertThat(LifeEngine.step(empty, 4, 4)).isEqualTo(empty);
        assertThat(StateCodec.isExtinct(LifeEngine.step(empty, 4, 4))).isTrue();
    }

    @Test
    @DisplayName("single live cell dies of underpopulation")
    void singleCellDies() {
        String center = rows(
                "000",
                "010",
                "000");
        String corner = rows(
                "100",
                "000",
                "000");
        String dead = "000000000";

        assertThat(LifeEngine.step(center, 3, 3)).isEqualTo(dead);
        assertThat(LifeEngine.step(corner, 3, 3)).isEqualTo(dead);
        assertThat(LifeEngine.step("1", 1, 1)).isEqualTo("0");
    }

    @Test
    @DisplayName("fully live board collapses to its four corners")
    void fullBoardCollapses() {
        String full = rows(
                "1111",
                "1111",
                "1111",
                "1111");
        String corners = rows(
                "1001",
                "0000",
                "0000",
                "1001");

        assertThat(LifeEngine.step(full, 4, 4)).isEqualTo(corners);
        assertThat(advance(full, 4, 4, 2)).isEqualTo("0000000000000000");
    }

    @Test
    @DisplayName("1x1 and 1xN grids step without error")
    void degenerateGridShapes() {
        assertThat(LifeEngine.step("0", 1, 1)).isEqualTo("0");
        assertThat(LifeEngine.step("1", 1, 1)).isEqualTo("0");

        assertThat(LifeEngine.step("11111", 5, 1)).isEqualTo("01110");
        assertThat(LifeEngine.step("11111", 1, 5)).isEqualTo("01110");
        assertThat(LifeEngine.step("00000", 1, 5)).isEqualTo("00000");
        assertThat(LifeEngine.step("010", 3, 1)).isEqualTo("000");
    }

    private static final int GLIDER_SIZE = 6;

    /** Southeast glider in the top-left of a 6×6 board. Shared by the translation and boundary tests. */
    private static String glider() {
        return rows(
                "010000",
                "001000",
                "111000",
                "000000",
                "000000",
                "000000");
    }

    private static String edge(String state, int row) {
        return state.substring(row * GLIDER_SIZE, (row + 1) * GLIDER_SIZE);
    }

    private static String advance(String state, int width, int height, int generations) {
        String current = state;
        for (int i = 0; i < generations; i++) {
            current = LifeEngine.step(current, width, height);
        }
        return current;
    }

    private static String rows(String... lines) {
        int width = lines[0].length();
        StringBuilder board = new StringBuilder(width * lines.length);
        for (String line : lines) {
            if (line.length() != width || !line.matches("[01]+")) {
                throw new IllegalArgumentException("row must be " + width + " of 0/1: " + line);
            }
            board.append(line);
        }
        return board.toString();
    }
}
