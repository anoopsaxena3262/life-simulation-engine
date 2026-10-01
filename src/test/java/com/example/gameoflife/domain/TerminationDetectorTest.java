package com.example.gameoflife.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TerminationDetectorTest {

    @Test
    @DisplayName("still life is detected as a fixed point with period 1")
    void detectsFixedPoint() {
        // TODO: block -> FIXED_POINT, period 1.
    }

    @Test
    @DisplayName("blinker is detected as a cycle with period 2")
    void detectsCycle() {
        // TODO: the case that makes stillness-only detection wrong.
    }

    @Test
    @DisplayName("board that dies out is reported as extinct")
    void detectsExtinction() {
        // TODO: single cell -> EXTINCT.
    }

    @Test
    @DisplayName("returns empty when no conclusion is reached within the limit")
    void noConclusionWithinLimit() {
        // TODO: use a deliberately small maxGenerations so the test stays fast.
    }

    @Test
    @DisplayName("reports the generation where the cycle first occurred")
    void reportsCycleEntryPoint() {
        // TODO: a pattern that settles into an oscillator after a few generations.
    }
}
