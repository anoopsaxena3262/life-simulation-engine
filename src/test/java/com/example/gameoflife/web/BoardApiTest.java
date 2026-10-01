package com.example.gameoflife.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end coverage of every endpoint and every error path in DESIGN.md section 5.
 * Method names here are referenced by the traceability table in that document --
 * keep them in step.
 */
@SpringBootTest
class BoardApiTest {

    @Test
    @DisplayName("F1: POST /boards returns 201 with an id and a Location header")
    void createBoardReturnsIdAndLocation() {
        // TODO
    }

    @Test
    @DisplayName("F2: GET /next returns the following generation")
    void nextReturnsFollowingGeneration() {
        // TODO
    }

    @Test
    @DisplayName("F2: calling /next twice returns the same generation both times")
    void nextIsIdempotent() {
        // TODO: reads are pure; the stored board is never advanced by a query.
    }

    @Test
    @DisplayName("F3: GET /generations/{n} returns the expected state")
    void generationsAtIndexReturnsExpectedState() {
        // TODO
    }

    @Test
    @DisplayName("F4: GET /final returns the state and termination metadata")
    void finalReturnsTerminationMetadata() {
        // TODO
    }

    @Test
    @DisplayName("F4: GET /final returns 422 when the generation limit is exceeded")
    void finalReturns422WhenLimitExceeded() {
        // TODO
    }

    @Test
    @DisplayName("unknown board id returns 404 with a problem detail body")
    void unknownBoardReturns404() {
        // TODO
    }

    @Test
    @DisplayName("malformed upload returns 400 with a problem detail body")
    void malformedUploadReturns400() {
        // TODO
    }

    @Test
    @DisplayName("negative generation index returns 400")
    void negativeIndexReturns400() {
        // TODO
    }
}
