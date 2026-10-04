package life.simulation.engine.service.exception;

/** The uploaded board, generation index, or maxGenerations value is not acceptable. Surfaces as 400. */
public class InvalidBoardException extends RuntimeException {

    public InvalidBoardException(String message) {
        super(message);
    }
}
