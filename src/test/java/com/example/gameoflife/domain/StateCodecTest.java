package com.example.gameoflife.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StateCodecTest {

    @Test
    @DisplayName("round trip preserves an arbitrary board")
    void roundTripPreservesBoard() {
        // TODO: generate random grids, serialize then deserialize, assert equality.
    }

    @Test
    @DisplayName("serialises a horizontal blinker to 000111000")
    void serialisesKnownPattern() {
        // TODO: pin the encoding with one hand-written example so a change is visible.
    }

    @Test
    @DisplayName("rejects a state whose length does not match width * height")
    void rejectsLengthMismatch() {
        // TODO: assertThatThrownBy -> IllegalArgumentException, not StringIndexOutOfBounds.
    }

    @Test
    @DisplayName("rejects a state containing a character other than 0 or 1")
    void rejectsUnexpectedCharacter() {
        // TODO
    }
}
