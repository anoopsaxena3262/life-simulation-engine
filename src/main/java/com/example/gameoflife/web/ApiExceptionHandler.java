package com.example.gameoflife.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.gameoflife.service.exception.BoardNotFoundException;
import com.example.gameoflife.service.exception.InvalidBoardException;
import com.example.gameoflife.service.exception.NoConclusionException;

/**
 * RFC 7807 problem responses. Every error path carries a body explaining what went
 * wrong -- an empty 400 tells the caller nothing.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BoardNotFoundException.class)
    public ProblemDetail onNotFound(BoardNotFoundException ex) {
        // TODO: ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage())
        // TODO: setTitle("Board not found")
        throw new UnsupportedOperationException("not implemented");
    }

    @ExceptionHandler(InvalidBoardException.class)
    public ProblemDetail onInvalid(InvalidBoardException ex) {
        // TODO: 400 with ex.getMessage() as detail.
        throw new UnsupportedOperationException("not implemented");
    }

    @ExceptionHandler(NoConclusionException.class)
    public ProblemDetail onNoConclusion(NoConclusionException ex) {
        // TODO: 422 UNPROCESSABLE_ENTITY -- the limit being reached is a documented outcome.
        // TODO: setProperty("generationsAttempted", ex.getGenerationsAttempted())
        throw new UnsupportedOperationException("not implemented");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidationFailure(MethodArgumentNotValidException ex) {
        // TODO: 400, summarising the field errors from ex.getBindingResult().
        throw new UnsupportedOperationException("not implemented");
    }
}
