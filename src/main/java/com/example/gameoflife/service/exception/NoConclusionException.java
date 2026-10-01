package com.example.gameoflife.service.exception;

/**
 * The board reached neither a fixed point nor a cycle within the generation limit.
 *
 * <p>Surfaces as 422, not 500: hitting the limit is a documented outcome of the
 * request, not a server fault.
 */
public class NoConclusionException extends RuntimeException {

    private final int generationsAttempted;

    public NoConclusionException(int generationsAttempted) {
        super("No conclusion reached within " + generationsAttempted + " generations");
        this.generationsAttempted = generationsAttempted;
    }

    public int getGenerationsAttempted() {
        return generationsAttempted;
    }
}
