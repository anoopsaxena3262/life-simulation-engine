package life.simulation.engine.web;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

import life.simulation.engine.service.exception.BoardNotFoundException;
import life.simulation.engine.service.exception.InvalidBoardException;
import life.simulation.engine.service.exception.NoConclusionException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void missingBoardIs404WithATitle() {
        var problem = handler.onNotFound(new BoardNotFoundException(UUID.randomUUID()));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Board not found");
        assertThat(problem.getDetail()).contains("No board");
    }

    @Test
    void invalidBoardIs400WithATitle() {
        var problem = handler.onInvalid(new InvalidBoardException("Width and height must be positive"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid board");
        assertThat(problem.getDetail()).isEqualTo("Width and height must be positive");
    }

    @Test
    void wrappedRequestTooLargeKeepsItsTitle() {
        var tooLarge = new RequestTooLargeException(2_000_000);
        var wrapped = new HttpMessageNotReadableException(
                "JSON parse error",
                new IllegalStateException(tooLarge),
                new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                wrapped,
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest()));

        var problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Request too large");
        assertThat(problem.getDetail()).contains("2000000");
    }

    @Test
    void noConclusionIs422AndReportsHowFarTheWalkGot() {
        var problem = handler.onNoConclusion(new NoConclusionException(1));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getTitle()).isEqualTo("No conclusion");
        assertThat(problem.getProperties()).containsEntry("generationsAttempted", 1);
    }

    @Test
    void fieldValidationListsEachFieldWithoutATrailingSeparator() throws Exception {
        var problem = validationProblem(
                new FieldError("request", "width", "must be greater than or equal to 1"),
                new FieldError("request", "height", "must be greater than or equal to 1"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Validation failed");
        assertThat(problem.getDetail())
                .isEqualTo("width: must be greater than or equal to 1; height: must be greater than or equal to 1");
    }

    @Test
    void globalValidationUsesTheObjectMessage() throws Exception {
        var problem = validationProblem(new ObjectError("request", "upload is empty"));

        assertThat(problem.getDetail()).isEqualTo("upload is empty");
    }

    @Test
    void validationWithNoMessagesStillExplainsTheFailure() throws Exception {
        var problem = validationProblem();

        assertThat(problem.getDetail()).isEqualTo("Request validation failed");
    }

    private ProblemDetail validationProblem(ObjectError... errors) throws Exception {
        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                validationFailure(errors),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest()));
        return (ProblemDetail) response.getBody();
    }

    private static MethodArgumentNotValidException validationFailure(ObjectError... errors) throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        for (ObjectError error : errors) {
            result.addError(error);
        }
        Method method = ApiExceptionHandler.class.getDeclaredMethod("onInvalid", InvalidBoardException.class);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), result);
    }
}
