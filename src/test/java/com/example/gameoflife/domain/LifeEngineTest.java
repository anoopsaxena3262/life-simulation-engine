package com.example.gameoflife.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules, exercised without an application context. These are the tests that
 * run in microseconds, which is the point of keeping the domain framework-free.
 */
class LifeEngineTest {

    @Test
    @DisplayName("block is a still life: unchanged after a step")
    void blockIsStable() {
        // TODO: 2x2 live square in a 4x4 grid, step, assert identical.
    }

    @Test
    @DisplayName("blinker oscillates with period 2")
    void blinkerOscillates() {
        // TODO: horizontal -> vertical -> horizontal.
    }

    @Test
    @DisplayName("toad oscillates with period 2")
    void toadOscillates() {
        // TODO
    }

    @Test
    @DisplayName("beacon oscillates with period 2")
    void beaconOscillates() {
        // TODO
    }

    @Test
    @DisplayName("glider translates diagonally over four generations")
    void gliderTranslates() {
        // TODO: assert the pattern reappears offset by one cell on each axis.
    }

    @Test
    @DisplayName("glider decays at the boundary rather than wrapping")
    void gliderDiesAtBoundary() {
        // TODO: this pins the topology decision -- dead borders, not a torus.
        // TODO: run a glider into the corner and assert it does not reappear on the far side.
    }

    @Test
    @DisplayName("empty board stays empty")
    void emptyBoardStaysEmpty() {
        // TODO
    }

    @Test
    @DisplayName("single live cell dies of underpopulation")
    void singleCellDies() {
        // TODO
    }

    @Test
    @DisplayName("fully live board collapses to its four corners")
    void fullBoardCollapses() {
        // TODO: interior cells are overpopulated; corners have exactly 3 neighbours.
    }

    @Test
    @DisplayName("1x1 and 1xN grids step without error")
    void degenerateGridShapes() {
        // TODO: edge shapes where the neighbourhood is mostly out of bounds.
    }
}
