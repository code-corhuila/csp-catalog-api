package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.domain.model.InvalidMovieException;
import co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.csp.catalog.domain.model.MovieNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Every answer that is not a success has the envelope and the codes of the contract (_shared.yaml). */
class ApiExceptionHandlerTest {

    private static final String CORRELATION = "550e8400-e29b-41d4-a716-446655440000";

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();

    @RestController
    static class FailingController {

        @GetMapping("/invalid-movie")
        void invalidMovie() {
            throw new InvalidMovieException("the duration must be at least one minute");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new MovieNotFoundException(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new IdempotencyKeyConflictException();
        }

        @GetMapping("/transition")
        void transition() {
            throw new InvalidStatusTransitionException("a PUBLISHED movie cannot become DRAFT");
        }

        @GetMapping("/bounds")
        void bounds() {
            throw new IllegalArgumentException("page starts at 1 and limit is between 1 and 100");
        }

        @GetMapping("/header")
        void header(@RequestHeader("Idempotency-Key") String key) {
        }

        @GetMapping("/unavailable")
        void unavailable() {
            throw new DataAccessResourceFailureException("mongodb://user:secret@host timed out");
        }

        @GetMapping("/transient")
        void transientFailure() {
            throw new TransientDataAccessResourceException("write conflict");
        }

        @GetMapping("/bug")
        void bug() {
            throw new IllegalStateException("password=hunter2 in a stack trace");
        }
    }

    @Test
    void aBrokenInvariantIsA400ValidationError() throws Exception {
        mvc.perform(get("/invalid-movie"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("the duration must be at least one minute"));
    }

    @Test
    void anUnknownMovieIsA404() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Movie 11111111-1111-4111-8111-111111111111 was not found"));
    }

    @Test
    void aKeyUsedWithAnotherRequestIsA409() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Idempotency key has already been used with a different payload"));
    }

    @Test
    void aForbiddenTransitionIsA422() throws Exception {
        mvc.perform(get("/transition"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value("a PUBLISHED movie cannot become DRAFT"));
    }

    @Test
    void aBoundOfTheContractThatWasBrokenIsA400WithTheBoundNamed() throws Exception {
        mvc.perform(get("/bounds"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("page starts at 1 and limit is between 1 and 100"));
    }

    @Test
    void aMissingRequiredHeaderIsA400() throws Exception {
        mvc.perform(get("/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid input data"));
    }

    @Test
    void anUnavailableDatabaseIsA503WithoutTheDriverMessage() throws Exception {
        mvc.perform(get("/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("A required service or dependency is unavailable"));
        mvc.perform(get("/transient")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void anUnexpectedFailureIsA500ThatLeaksNothing() throws Exception {
        mvc.perform(get("/bug"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void everyAnswerCarriesTheTraceIdOfTheCorrelationHeaderAndNoEmptyDetails() throws Exception {
        mvc.perform(get("/not-found").header("X-Correlation-Id", CORRELATION))
                .andExpect(header().string("X-Correlation-Id", CORRELATION))
                .andExpect(jsonPath("$.traceId").value(CORRELATION))
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
