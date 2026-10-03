package life.simulation.engine.domain;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins every field of {@link TerminationResult}: kind, the state at detection,
 * the generation where that state was first seen, the period, and how far the
 * walk went. See DESIGN.md sections 4.3 and 6.1.
 */
class TerminationDetectorTest {

    @Test
    @DisplayName("still life is detected as a fixed point with period 1")
    void detectsFixedPoint() {
        String block = rows(
                "0000",
                "0110",
                "0110",
                "0000");

        TerminationResult result = conclude(block, 4, 4, 10);

        assertThat(result).isEqualTo(new TerminationResult(
                TerminationKind.FIXED_POINT, block, 0, 1, 1));
    }

    @Test
    @DisplayName("blinker is detected as a cycle with period 2")
    void detectsCycle() {
        String horizontal = rows(
                "000",
                "111",
                "000");

        TerminationResult result = conclude(horizontal, 3, 3, 10);

        // Generation 2 returns to the start. Stillness-only detection would never stop.
        assertThat(result).isEqualTo(new TerminationResult(
                TerminationKind.CYCLE, horizontal, 0, 2, 2));
    }

    @Test
    @DisplayName("board that dies out is reported as extinct")
    void detectsExtinction() {
        String single = rows(
                "000",
                "010",
                "000");
        String dead = "000000000";

        TerminationResult result = conclude(single, 3, 3, 10);

        // The dead board first appears at generation 1 and is confirmed on the next step.
        assertThat(result).isEqualTo(new TerminationResult(
                TerminationKind.EXTINCT, dead, 1, 1, 2));
    }

    @Test
    @DisplayName("an already dead board is extinct at generation 0")
    void alreadyDeadBoardIsExtinctImmediately() {
        String dead = rows(
                "0000",
                "0000",
                "0000",
                "0000");

        TerminationResult result = conclude(dead, 4, 4, 10);

        assertThat(result).isEqualTo(new TerminationResult(
                TerminationKind.EXTINCT, dead, 0, 1, 1));
    }

    @Test
    @DisplayName("a still life reached later records that generation, not zero")
    void fixedPointAfterTransientRecordsFirstOccurrence() {
        String glider = rows(
                "010000",
                "001000",
                "111000",
                "000000",
                "000000",
                "000000");
        String cornerBlock = rows(
                "000000",
                "000000",
                "000000",
                "000000",
                "000011",
                "000011");

        TerminationResult result = conclude(glider, 6, 6, 100);

        // The corner block is generation 15; generation 16 only confirms it is stable.
        assertThat(result).isEqualTo(new TerminationResult(
                TerminationKind.FIXED_POINT, cornerBlock, 15, 1, 16));
    }

    @Test
    @DisplayName("extinction after a collapse records when the dead board first appeared")
    void extinctionAfterCollapseRecordsFirstOccurrence() {
        String full = rows(
                "1111",
                "1111",
                "1111",
                "1111");
        String dead = "0000000000000000";

        TerminationResult result = conclude(full, 4, 4, 10);

        assertThat(result).isEqualTo(new TerminationResult(
                TerminationKind.EXTINCT, dead, 2, 1, 3));
    }

    @Test
    @DisplayName("returns empty when no conclusion is reached within the limit")
    void noConclusionWithinLimit() {
        String horizontal = rows(
                "000",
                "111",
                "000");

        // One step reaches the vertical phase, which has not been seen before.
        Optional<TerminationResult> unfinished = TerminationDetector.detect(horizontal, 3, 3, 1);

        assertThat(unfinished).isEmpty();
    }

    @Test
    @DisplayName("reports the generation where the cycle first occurred")
    void reportsCycleEntryPoint() {
        // Plus sign. Generations 0–3 are transient; a period-2 oscillator begins at generation 4.
        String start = rows(
                "00000",
                "00100",
                "01110",
                "00100",
                "00000");
        String cycleState = rows(
                "01110",
                "10001",
                "10001",
                "10001",
                "01110");

        TerminationResult result = conclude(start, 5, 5, 20);

        assertThat(result).isEqualTo(new TerminationResult(
                TerminationKind.CYCLE, cycleState, 4, 2, 6));
    }

    private static TerminationResult conclude(String state, int width, int height, int maxGenerations) {
        return TerminationDetector.detect(state, width, height, maxGenerations).orElseThrow();
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
