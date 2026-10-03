package life.simulation.engine.service.exception;

/** The uploaded board is malformed or exceeds a configured limit. Surfaces as 400. */
public class InvalidBoardException extends RuntimeException {

    public InvalidBoardException(String message) {
        super(message);
    }
}
