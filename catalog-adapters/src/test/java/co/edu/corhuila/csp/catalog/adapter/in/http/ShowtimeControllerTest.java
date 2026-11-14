package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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

import co.edu.corhuila.csp.catalog.application.port.in.CancelShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.in.ShowtimeUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimePage;
import co.edu.corhuila.csp.catalog.domain.model.InvalidShowtimeException;
import co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeOverlapException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeReferenceException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import co.edu.corhuila.csp.catalog.domain.model.TimeInterval;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The routes of the showtimes: statuses, headers, DTOs and the data handed to the use cases (catalog-service.yaml). */
class ShowtimeControllerTest {

    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MOVIE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ROOM = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-11-01T20:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-11-01T22:00:00Z");
    private static final String KEY = "synthetic-key-0001";
    private static final String CREATE = """
            {"movieId":"11111111-1111-4111-8111-111111111111","roomId":"22222222-2222-4222-8222-222222222222",
             "startsAt":"2026-11-01T20:00:00Z","endsAt":"2026-11-01T22:00:00Z","priceCents":1500,
             "audioLanguage":"es","subtitleLanguage":"en"}""";

    private final ShowtimeUseCases useCases = mock(ShowtimeUseCases.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ShowtimeController(useCases))
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                    .findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .addFilters(new CorrelationIdFilter())
            .build();

    private static Showtime showtime(String audio, String subtitle, ShowtimeStatus status) {
        return new Showtime(ID, MOVIE, ROOM, "Synthetic Movie", "Synthetic Room", new TimeInterval(STARTS, ENDS),
                1500, audio, subtitle, status, NOW, NOW);
    }

    private static Showtime showtime() {
        return showtime("es", "en", ShowtimeStatus.SCHEDULED);
    }

    private static MockHttpServletRequestBuilder creation(String body) {
        return post("/showtimes").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder update(String body) {
        return patch("/showtimes/" + ID).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void schedulingAShowtimeAnswers201WithItsLocationAndEveryFieldOfTheContract() throws Exception {
        when(useCases.createShowtime(any())).thenReturn(showtime());

        mvc.perform(creation(CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/showtimes/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.movieId").value(MOVIE.toString()))
                .andExpect(jsonPath("$.roomId").value(ROOM.toString()))
                .andExpect(jsonPath("$.startsAt").value("2026-11-01T20:00:00Z"))
                .andExpect(jsonPath("$.endsAt").value("2026-11-01T22:00:00Z"))
                .andExpect(jsonPath("$.priceCents").value(1500))
                .andExpect(jsonPath("$.audioLanguage").value("es"))
                .andExpect(jsonPath("$.subtitleLanguage").value("en"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.movieTitleSnapshot").doesNotExist())
                .andExpect(jsonPath("$.roomNameSnapshot").doesNotExist());
    }

    @Test
    void theCreationHandsTheKeyAndEveryValueOfTheRequestToTheUseCase() throws Exception {
        when(useCases.createShowtime(any())).thenReturn(showtime());

        mvc.perform(creation(CREATE));

        ArgumentCaptor<CreateShowtimeInput> input = ArgumentCaptor.forClass(CreateShowtimeInput.class);
        verify(useCases).createShowtime(input.capture());
        assertEquals(new CreateShowtimeInput(KEY, MOVIE, ROOM, STARTS, ENDS, 1500, "es", "en"), input.getValue());
    }

    @Test
    void theLanguagesAreOptionalAndLeftOutOfTheAnswerWhenTheShowtimeHasNone() throws Exception {
        when(useCases.createShowtime(any())).thenReturn(showtime(null, null, ShowtimeStatus.SCHEDULED));

        mvc.perform(creation("""
                {"movieId":"11111111-1111-4111-8111-111111111111","roomId":"22222222-2222-4222-8222-222222222222",
                 "startsAt":"2026-11-01T20:00:00Z","endsAt":"2026-11-01T22:00:00Z","priceCents":0}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audioLanguage").doesNotExist())
                .andExpect(jsonPath("$.subtitleLanguage").doesNotExist());

        ArgumentCaptor<CreateShowtimeInput> input = ArgumentCaptor.forClass(CreateShowtimeInput.class);
        verify(useCases).createShowtime(input.capture());
        assertNull(input.getValue().audioLanguage());
        assertNull(input.getValue().subtitleLanguage());
        assertEquals(0, input.getValue().priceCents());
    }

    @Test
    void aMissingRequiredFieldIsA400WithTheEnvelopeAndTheUseCaseIsNeverCalled() throws Exception {
        for (String field : List.of("movieId", "roomId", "startsAt", "endsAt", "priceCents")) {
            String body = CREATE.replaceAll("\"" + field + "\":(\"[^\"]*\"|\\d+),?", "");
            mvc.perform(creation(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(useCases);
    }

    @Test
    void aValueOfTheWrongTypeOrABrokenBodyIsA400() throws Exception {
        mvc.perform(creation(CREATE.replace("2026-11-01T20:00:00Z", "tomorrow"))).andExpect(status().isBadRequest());
        mvc.perform(creation(CREATE.replace("11111111-1111-4111-8111-111111111111", "not-a-uuid")))
                .andExpect(status().isBadRequest());
        mvc.perform(creation(CREATE.replace("1500", "\"cheap\""))).andExpect(status().isBadRequest());
        mvc.perform(creation("{")).andExpect(status().isBadRequest());

        verifyNoInteractions(useCases);
    }

    @Test
    void theIdempotencyKeyIsRequiredAndBetween8And128CharactersOnTheThreeWrites() throws Exception {
        mvc.perform(post("/showtimes").contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/showtimes").header("Idempotency-Key", "short").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("the Idempotency-Key must have between 8 and 128 characters"));
        mvc.perform(post("/showtimes").header("Idempotency-Key", "k".repeat(129))
                .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/showtimes/" + ID).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/showtimes/" + ID).header("Idempotency-Key", "short")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/showtimes/" + ID)).andExpect(status().isBadRequest());
        mvc.perform(delete("/showtimes/" + ID).header("Idempotency-Key", "k".repeat(129)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCases);
    }

    @Test
    void aBrokenInvariantIsA400AndAnOverlapIsA409() throws Exception {
        when(useCases.createShowtime(any())).thenThrow(new InvalidShowtimeException("the price cannot be negative"));

        mvc.perform(creation(CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("the price cannot be negative"));

        doThrow(new ShowtimeOverlapException(ID)).when(useCases).createShowtime(any());

        mvc.perform(creation(CREATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void aMovieOrRoomThatCannotBeUsedIsA422AndAKeyUsedWithAnotherRequestIsA409() throws Exception {
        when(useCases.createShowtime(any())).thenThrow(new ShowtimeReferenceException("the movie does not exist"));

        mvc.perform(creation(CREATE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("the movie does not exist"));

        doThrow(new IdempotencyKeyConflictException()).when(useCases).createShowtime(any());

        mvc.perform(creation(CREATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void aShowtimeIsReadByIdWhateverItsStatus() throws Exception {
        when(useCases.getShowtime(ID)).thenReturn(showtime("es", "en", ShowtimeStatus.CANCELLED));

        mvc.perform(get("/showtimes/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void anUnknownShowtimeIsA404AndAPathThatIsNotAUuidIsA400() throws Exception {
        when(useCases.getShowtime(ID)).thenThrow(new ShowtimeNotFoundException(ID));

        mvc.perform(get("/showtimes/" + ID)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mvc.perform(get("/showtimes/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(patch("/showtimes/not-a-uuid").header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/showtimes/not-a-uuid").header("Idempotency-Key", KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theListUsesPage1AndLimit20ByDefaultAndReportsThePaginationMetadata() throws Exception {
        when(useCases.listShowtimes(1, 20)).thenReturn(new ShowtimePage(List.of(showtime()), 1, 20, 41));

        mvc.perform(get("/showtimes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data[0].priceCents").value(1500))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.total").value(41))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
    }

    @Test
    void theListTakesThePageAndTheLimitOfTheRequestAndAnEmptyListHasNoPages() throws Exception {
        when(useCases.listShowtimes(2, 10)).thenReturn(new ShowtimePage(List.of(), 2, 10, 0));

        mvc.perform(get("/showtimes?page=2&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void aWindowOutOfBoundsOrNotANumberIsA400() throws Exception {
        when(useCases.listShowtimes(1, 101))
                .thenThrow(new IllegalArgumentException("page starts at 1 and limit is between 1 and 100"));

        mvc.perform(get("/showtimes?limit=101")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page starts at 1 and limit is between 1 and 100"));
        mvc.perform(get("/showtimes?page=one")).andExpect(status().isBadRequest());
    }

    @Test
    void aPartialUpdateHandsOnlyTheFieldsThatWereSentAndAnswers200() throws Exception {
        when(useCases.updateShowtime(any())).thenReturn(showtime());

        mvc.perform(update("{\"priceCents\":2000,\"endsAt\":\"2026-11-01T23:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));

        ArgumentCaptor<UpdateShowtimeInput> input = ArgumentCaptor.forClass(UpdateShowtimeInput.class);
        verify(useCases).updateShowtime(input.capture());
        assertEquals(KEY, input.getValue().idempotencyKey());
        assertEquals(ID, input.getValue().showtimeId());
        assertEquals(2000L, input.getValue().changes().priceCents());
        assertEquals(Instant.parse("2026-11-01T23:00:00Z"), input.getValue().changes().endsAt());
        assertNull(input.getValue().changes().startsAt());
        assertNull(input.getValue().changes().audioLanguage());
        assertNull(input.getValue().changes().subtitleLanguage());
        assertNull(input.getValue().changes().status());
    }

    @Test
    void anUpdateCanChangeTheStartTheLanguagesAndTheStatus() throws Exception {
        when(useCases.updateShowtime(any())).thenReturn(showtime());

        mvc.perform(update("""
                {"startsAt":"2026-11-01T19:00:00Z","audioLanguage":"fr","subtitleLanguage":"de","status":"COMPLETED"}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateShowtimeInput> input = ArgumentCaptor.forClass(UpdateShowtimeInput.class);
        verify(useCases).updateShowtime(input.capture());
        assertEquals(Instant.parse("2026-11-01T19:00:00Z"), input.getValue().changes().startsAt());
        assertEquals("fr", input.getValue().changes().audioLanguage());
        assertEquals("de", input.getValue().changes().subtitleLanguage());
        assertEquals(ShowtimeStatus.COMPLETED, input.getValue().changes().status());
        assertNull(input.getValue().changes().priceCents());
    }

    @Test
    void aStatusOutsideTheEnumIsA400AndTheUseCaseIsNeverCalled() throws Exception {
        mvc.perform(update("{\"status\":\"POSTPONED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(useCases);
    }

    @Test
    void anUpdateOfAShowtimeThatCannotChangeIsA422AndOfAnUnknownOneIsA404() throws Exception {
        when(useCases.updateShowtime(any()))
                .thenThrow(new InvalidStatusTransitionException("a CANCELLED showtime cannot be changed"));

        mvc.perform(update("{\"priceCents\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));

        doThrow(new ShowtimeNotFoundException(ID)).when(useCases).updateShowtime(any());

        mvc.perform(update("{\"priceCents\":1}")).andExpect(status().isNotFound());

        doThrow(new ShowtimeOverlapException(ID)).when(useCases).updateShowtime(any());

        mvc.perform(update("{\"priceCents\":1}")).andExpect(status().isConflict());
    }

    @Test
    void cancellingAnswers204WithoutBodyAndHandsTheKeyAndTheIdToTheUseCase() throws Exception {
        mvc.perform(delete("/showtimes/" + ID).header("Idempotency-Key", KEY))
                .andExpect(status().isNoContent());

        ArgumentCaptor<CancelShowtimeInput> input = ArgumentCaptor.forClass(CancelShowtimeInput.class);
        verify(useCases).cancelShowtime(input.capture());
        assertEquals(new CancelShowtimeInput(KEY, ID), input.getValue());
    }

    @Test
    void anUnknownShowtimeCannotBeCancelledAndACancelledOneAnswers422() throws Exception {
        doThrow(new ShowtimeNotFoundException(ID)).when(useCases).cancelShowtime(any());

        mvc.perform(delete("/showtimes/" + ID).header("Idempotency-Key", KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        doThrow(new InvalidStatusTransitionException("a CANCELLED showtime cannot be cancelled"))
                .when(useCases).cancelShowtime(any());

        mvc.perform(delete("/showtimes/" + ID).header("Idempotency-Key", KEY))
                .andExpect(status().isUnprocessableEntity());
    }
}
