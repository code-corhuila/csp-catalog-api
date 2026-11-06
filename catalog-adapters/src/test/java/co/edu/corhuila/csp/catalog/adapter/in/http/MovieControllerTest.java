package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.csp.catalog.application.port.in.ArchiveMovieInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateMovieInput;
import co.edu.corhuila.csp.catalog.application.port.in.MovieUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateMovieInput;
import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.MovieNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The routes of the movies: statuses, headers, DTOs and the data handed to the use cases (catalog-service.yaml). */
class MovieControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final String KEY = "synthetic-key-0001";
    private static final String CREATE = """
            {"title":"Synthetic Movie","synopsis":"A synopsis","durationMinutes":120,"classification":"PG-13",
             "releaseDate":"2026-12-01","genres":["Drama"],"cast":["Synthetic Actor"],
             "posterUrl":"https://example.com/poster.png","status":"DRAFT"}""";

    private final MovieUseCases useCases = mock(MovieUseCases.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MovieController(useCases))
            .setControllerAdvice(new ApiExceptionHandler())
            // the mapper of the service: a date is written as 2026-12-01, as the contract declares (application.yml)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                    .findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .addFilters(new CorrelationIdFilter())
            .build();

    private static Movie movie(PublicationStatus status) {
        return Movie.create(ID, new MovieDetails("Synthetic Movie", "A synopsis", 120, "PG-13",
                LocalDate.of(2026, 12, 1), List.of("Drama"), List.of("Synthetic Actor"),
                URI.create("https://example.com/poster.png")), PublicationStatus.DRAFT, NOW).withStatus(status, NOW);
    }

    @Test
    void creatingAMovieAnswers201WithItsLocationAndEveryFieldOfTheContract() throws Exception {
        when(useCases.createMovie(any())).thenReturn(movie(PublicationStatus.DRAFT));

        mvc.perform(post("/movies").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/movies/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.title").value("Synthetic Movie"))
                .andExpect(jsonPath("$.synopsis").value("A synopsis"))
                .andExpect(jsonPath("$.durationMinutes").value(120))
                .andExpect(jsonPath("$.classification").value("PG-13"))
                .andExpect(jsonPath("$.releaseDate").value("2026-12-01"))
                .andExpect(jsonPath("$.genres[0]").value("Drama"))
                .andExpect(jsonPath("$.cast[0]").value("Synthetic Actor"))
                .andExpect(jsonPath("$.posterUrl").value("https://example.com/poster.png"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void theCreationHandsTheKeyTheDetailsAndTheStatusToTheUseCase() throws Exception {
        when(useCases.createMovie(any())).thenReturn(movie(PublicationStatus.PUBLISHED));

        mvc.perform(post("/movies").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(CREATE.replace("\"DRAFT\"", "\"PUBLISHED\"")));

        ArgumentCaptor<CreateMovieInput> input = ArgumentCaptor.forClass(CreateMovieInput.class);
        verify(useCases).createMovie(input.capture());
        assertEquals(KEY, input.getValue().idempotencyKey());
        assertEquals(PublicationStatus.PUBLISHED, input.getValue().status());
        assertEquals("Synthetic Movie", input.getValue().details().title());
        assertEquals(120, input.getValue().details().durationMinutes());
        assertEquals(LocalDate.of(2026, 12, 1), input.getValue().details().releaseDate());
    }

    @Test
    void aMovieWithoutOptionalFieldsIsAnsweredWithoutNullsBecauseTheContractDoesNotAllowThem() throws Exception {
        Movie minimal = Movie.create(ID, new MovieDetails("Synthetic Movie", null, 90, "G", null, null, null, null),
                PublicationStatus.DRAFT, NOW);
        when(useCases.getMovie(ID)).thenReturn(minimal);

        mvc.perform(get("/movies/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synopsis").doesNotExist())
                .andExpect(jsonPath("$.releaseDate").doesNotExist())
                .andExpect(jsonPath("$.posterUrl").doesNotExist())
                .andExpect(jsonPath("$.genres").isEmpty())
                .andExpect(jsonPath("$.cast").isEmpty());
    }

    @Test
    void aDurationOfZeroIsA400AndTheUseCaseIsNeverCalled() throws Exception {
        mvc.perform(post("/movies").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(CREATE.replace("\"durationMinutes\":120", "\"durationMinutes\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("the duration must be at least one minute"));

        verifyNoInteractions(useCases);
    }

    @Test
    void everyRequiredFieldIsRequired() throws Exception {
        for (String field : List.of("\"title\":\"Synthetic Movie\",", "\"durationMinutes\":120,",
                "\"classification\":\"PG-13\",")) {
            mvc.perform(post("/movies").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE.replace(field, "")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }
        mvc.perform(post("/movies").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Synthetic Movie\",\"durationMinutes\":120,\"classification\":\"PG-13\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("the status is required"));
        verifyNoInteractions(useCases);
    }

    @Test
    void aStatusOutsideTheEnumOrABrokenBodyIsA400() throws Exception {
        mvc.perform(post("/movies").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(CREATE.replace("\"DRAFT\"", "\"HIDDEN\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mvc.perform(post("/movies").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCases);
    }

    @Test
    void theIdempotencyKeyIsRequiredAndBetween8And128Characters() throws Exception {
        mvc.perform(post("/movies").contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/movies").header("Idempotency-Key", "short").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("the Idempotency-Key must have between 8 and 128 characters"));
        mvc.perform(post("/movies").header("Idempotency-Key", "k".repeat(129)).contentType(MediaType.APPLICATION_JSON)
                .content(CREATE))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/movies/" + ID).header("Idempotency-Key", "short").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/movies/" + ID)).andExpect(status().isBadRequest());
        verifyNoInteractions(useCases);
    }

    @Test
    void aKeyUsedWithAnotherRequestIsA409() throws Exception {
        when(useCases.createMovie(any())).thenThrow(new IdempotencyKeyConflictException());

        mvc.perform(post("/movies").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void aMovieIsReadById() throws Exception {
        when(useCases.getMovie(ID)).thenReturn(movie(PublicationStatus.PUBLISHED));

        mvc.perform(get("/movies/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void anUnknownMovieIsA404AndAPathThatIsNotAUuidIsA400() throws Exception {
        when(useCases.getMovie(ID)).thenThrow(new MovieNotFoundException(ID));

        mvc.perform(get("/movies/" + ID)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mvc.perform(get("/movies/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void theListUsesPage1AndLimit20ByDefaultAndReportsThePaginationMetadata() throws Exception {
        when(useCases.listMovies(1, 20)).thenReturn(new MoviePage(List.of(movie(PublicationStatus.DRAFT)), 1, 20, 41));

        mvc.perform(get("/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.total").value(41))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
    }

    @Test
    void theListTakesThePageAndTheLimitOfTheRequestAndAnEmptyListHasNoPages() throws Exception {
        when(useCases.listMovies(2, 10)).thenReturn(new MoviePage(List.of(), 2, 10, 0));

        mvc.perform(get("/movies?page=2&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void aWindowOutOfBoundsOrNotANumberIsA400() throws Exception {
        when(useCases.listMovies(1, 101)).thenThrow(new IllegalArgumentException("page starts at 1 and limit is between 1 and 100"));

        mvc.perform(get("/movies?limit=101")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page starts at 1 and limit is between 1 and 100"));
        mvc.perform(get("/movies?page=one")).andExpect(status().isBadRequest());
    }

    @Test
    void aPartialUpdateHandsOnlyTheFieldsThatWereSentAndAnswers200() throws Exception {
        when(useCases.updateMovie(any())).thenReturn(movie(PublicationStatus.PUBLISHED));

        mvc.perform(patch("/movies/" + ID).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New title\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        ArgumentCaptor<UpdateMovieInput> input = ArgumentCaptor.forClass(UpdateMovieInput.class);
        verify(useCases).updateMovie(input.capture());
        assertEquals(KEY, input.getValue().idempotencyKey());
        assertEquals(ID, input.getValue().movieId());
        assertEquals("New title", input.getValue().changes().title());
        assertEquals(PublicationStatus.PUBLISHED, input.getValue().changes().status());
        assertNull(input.getValue().changes().durationMinutes());
        assertNull(input.getValue().changes().synopsis());
    }

    @Test
    void aForbiddenTransitionOfAnUpdateIsA422() throws Exception {
        when(useCases.updateMovie(any())).thenThrow(new InvalidStatusTransitionException("a PUBLISHED movie cannot become DRAFT"));

        mvc.perform(patch("/movies/" + ID).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void archivingAnswers204WithoutBodyAndHandsTheKeyAndTheIdToTheUseCase() throws Exception {
        mvc.perform(delete("/movies/" + ID).header("Idempotency-Key", KEY))
                .andExpect(status().isNoContent());

        ArgumentCaptor<ArchiveMovieInput> input = ArgumentCaptor.forClass(ArchiveMovieInput.class);
        verify(useCases).archiveMovie(input.capture());
        assertEquals(KEY, input.getValue().idempotencyKey());
        assertEquals(ID, input.getValue().movieId());
    }

    @Test
    void anUnknownMovieCannotBeArchived() throws Exception {
        doThrow(new MovieNotFoundException(ID)).when(useCases).archiveMovie(any());

        mvc.perform(delete("/movies/" + ID).header("Idempotency-Key", KEY)).andExpect(status().isNotFound());
        verify(useCases, never()).createMovie(any());
    }
}
