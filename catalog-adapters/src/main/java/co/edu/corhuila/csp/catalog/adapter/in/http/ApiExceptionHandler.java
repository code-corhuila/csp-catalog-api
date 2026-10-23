package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.RoomNameAlreadyInUseException;
import co.edu.corhuila.csp.catalog.domain.model.InvalidMovieException;
import co.edu.corhuila.csp.catalog.domain.model.InvalidRoomException;
import co.edu.corhuila.csp.catalog.domain.model.InvalidShowtimeException;
import co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.csp.catalog.domain.model.MovieNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.RoomNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeOverlapException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeReferenceException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The only place where a domain or infrastructure error becomes an HTTP answer, in the status codes and
 * the {@code error} codes of {@code catalog-service.yaml}. The domain never knows how an error is shown,
 * and no answer carries a stack trace, a driver message or a token: the detail goes to the log under the
 * traceId of the request.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 400: a movie that no valid movie can be (HU-CATALOG-001, scenario 2), named by its broken rule. */
    @ExceptionHandler(InvalidMovieException.class)
    ResponseEntity<ErrorResponse> invalidMovie(InvalidMovieException exception) {
        return answer(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    /** 400: a room that no valid room can be (HU-CATALOG-002), named by its broken rule. */
    @ExceptionHandler(InvalidRoomException.class)
    ResponseEntity<ErrorResponse> invalidRoom(InvalidRoomException exception) {
        return answer(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    /** 400: a showtime that no valid showtime can be (HU-CATALOG-003), named by its broken rule. */
    @ExceptionHandler(InvalidShowtimeException.class)
    ResponseEntity<ErrorResponse> invalidShowtime(InvalidShowtimeException exception) {
        return answer(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    /** 400: a bound of the contract that the ports guard, for example the window of a list. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> outOfBounds(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Invalid input data" : exception.getMessage();
        return answer(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** 400: a request the router or the deserializer cannot make sense of. */
    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ErrorResponse> unreadableRequest() {
        return answer(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid input data");
    }

    /** 404: no movie with that id. */
    @ExceptionHandler(MovieNotFoundException.class)
    ResponseEntity<ErrorResponse> movieNotFound(MovieNotFoundException exception) {
        return answer(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    /** 404: no active room has that id; an archived room is not found. */
    @ExceptionHandler(RoomNotFoundException.class)
    ResponseEntity<ErrorResponse> roomNotFound(RoomNotFoundException exception) {
        return answer(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    /** 404: no showtime has that id; a cancelled one is found. */
    @ExceptionHandler(ShowtimeNotFoundException.class)
    ResponseEntity<ErrorResponse> showtimeNotFound(ShowtimeNotFoundException exception) {
        return answer(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    /** 404: nothing answers that path. */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ErrorResponse> noRoute() {
        return answer(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource does not exist");
    }

    /** 409: the key was already used with another request and nothing was written (Norma 5.3.8). */
    @ExceptionHandler(IdempotencyKeyConflictException.class)
    ResponseEntity<ErrorResponse> conflict(IdempotencyKeyConflictException exception) {
        return answer(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    /** 409: another room has that name, an archived one included (ADR-038); the unique index decides. */
    @ExceptionHandler(RoomNameAlreadyInUseException.class)
    ResponseEntity<ErrorResponse> roomNameInUse(RoomNameAlreadyInUseException exception) {
        return answer(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    /** 409: another scheduled showtime occupies the room during that interval (ADR-039). */
    @ExceptionHandler(ShowtimeOverlapException.class)
    ResponseEntity<ErrorResponse> showtimeOverlap(ShowtimeOverlapException exception) {
        return answer(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    /** 422: the movie is missing or not published, or the room is missing or archived (ADR-039). */
    @ExceptionHandler(ShowtimeReferenceException.class)
    ResponseEntity<ErrorResponse> showtimeReference(ShowtimeReferenceException exception) {
        return answer(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", exception.getMessage());
    }

    /** 422: the lifecycle of ADR-037 forbids the change, for example publishing an archived movie. */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    ResponseEntity<ErrorResponse> invalidTransition(InvalidStatusTransitionException exception) {
        return answer(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATUS_TRANSITION", exception.getMessage());
    }

    /** 503: the database cannot be reached or kept the transaction from starting. */
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<ErrorResponse> unavailable(HttpServletRequest request, Exception exception) {
        log.warn("dependency unavailable answering {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getClass().getSimpleName());
        return answer(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "A required service or dependency is unavailable");
    }

    /** 500: everything else, logged with its stack trace under the traceId of the request. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(HttpServletRequest request, Exception exception) {
        log.error("unexpected error answering {} {}", request.getMethod(), request.getRequestURI(), exception);
        return answer(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    private static ResponseEntity<ErrorResponse> answer(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(error, message));
    }
}
