package life.simulation.engine.web;

/**
 * The body was larger than {@code game-of-life.max-request-bytes}. Thrown while the
 * body is being read, so the grid is never built.
 */
public class RequestTooLargeException extends RuntimeException {

    public RequestTooLargeException(long maxBytes) {
        super("Request body exceeds " + maxBytes + " bytes");
    }
}
