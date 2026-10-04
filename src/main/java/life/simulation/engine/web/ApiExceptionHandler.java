package life.simulation.engine.web;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import life.simulation.engine.service.exception.BoardNotFoundException;
import life.simulation.engine.service.exception.InvalidBoardException;
import life.simulation.engine.service.exception.NoConclusionException;

/**
 * RFC 7807 problem responses.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} is required. With
 * {@code spring.mvc.problemdetails.enabled=true}, Spring Boot otherwise registers its
 * own handler at a higher order, and that handler answers
 * {@link MethodArgumentNotValidException} before a plain {@code @ExceptionHandler} runs.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BoardNotFoundException.class)
    public ProblemDetail onNotFound(BoardNotFoundException ex) {
        // 404: the id is well formed, but no board is stored under it.
        log.warn("board not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Board not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidBoardException.class)
    public ProblemDetail onInvalid(InvalidBoardException ex) {
        // 400: the board itself is malformed or over a limit. The request is the problem.
        log.warn("invalid board: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid board", ex.getMessage());
    }

    @ExceptionHandler(NoConclusionException.class)
    public ProblemDetail onNoConclusion(NoConclusionException ex) {
        // 422: the walk used its allowed generations and did not repeat. That is a
        // documented outcome, not a server fault, so it stays a client error.
        log.info("no conclusion after {} generations", ex.getGenerationsAttempted());
        ProblemDetail detail = problem(
                HttpStatus.UNPROCESSABLE_ENTITY, "No conclusion", ex.getMessage());
        detail.setProperty("generationsAttempted", ex.getGenerationsAttempted());
        return detail;
    }

    @ExceptionHandler(RequestTooLargeException.class)
    public ProblemDetail onTooLarge(RequestTooLargeException ex) {
        // 400: the body grew past the cap while it was read. A Content-Length over the
        // cap is answered in the filter and never reaches this method. When the stream
        // fails inside a JSON property, Jackson wraps this exception; that path is
        // unwrapped in handleHttpMessageNotReadable.
        log.warn("request too large: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Request too large", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        // Jackson's WRAP_EXCEPTIONS is on by default. A size-limit failure during the
        // first buffer fill can arrive as RequestTooLargeException itself. The same
        // failure while reading inside a property arrives wrapped, and Spring then
        // reports an unreadable body. Both answers are "Request too large".
        RequestTooLargeException tooLarge = findCause(ex, RequestTooLargeException.class);
        if (tooLarge != null) {
            ProblemDetail body = onTooLarge(tooLarge);
            return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        // 400: @Valid rejected the JSON before the controller method ran.
        String message = validationMessage(ex);
        log.warn("validation failed: {}", message);
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed", message);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static <T extends Throwable> T findCause(Throwable thrown, Class<T> type) {
        Throwable current = thrown;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    // Creates a ProblemDetail object with the given status, title, and detail
    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    // Creates a string message from the validation errors
    private static String validationMessage(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors();
        if (!fieldErrors.isEmpty()) {
            return fieldErrors.stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        // Creates a string message from the global errors
        String global = ex.getBindingResult().getGlobalErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return global.isBlank() ? "Request validation failed" : global;
    }
}
