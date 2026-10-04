package life.simulation.engine.service.exception;

/**
 * The board reached neither a fixed point nor a cycle within the generation limit.
 *
 * <p>Surfaces as 422, not 500. Hitting the limit is a documented outcome of the
 * request, not a server fault. The HTTP handler logs it once, at INFO.
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
