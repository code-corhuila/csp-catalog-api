package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.csp.catalog.application.port.in.BillboardQuery;
import co.edu.corhuila.csp.catalog.application.port.in.BillboardUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardEntry;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardMovie;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardPage;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardRoom;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardShowtime;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The public billboard route: status, DTOs, window metadata and the query handed to the use case (catalog-service.yaml). */
class BillboardControllerTest {

    private static final UUID MOVIE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ROOM = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SHOWTIME = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant STARTS = Instant.parse("2026-11-01T20:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-11-01T22:00:00Z");

    private final BillboardUseCases useCases = mock(BillboardUseCases.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new BillboardController(useCases))
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                    .findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .addFilters(new CorrelationIdFilter())
            .build();

    private static BillboardEntry entry() {
        return new BillboardEntry(
                new BillboardMovie(MOVIE, "Synthetic Movie", "A synopsis", 120, List.of("Drama", "Comedy"), "PG-13",
                        URI.create("https://example.com/poster.png"), LocalDate.parse("2026-12-01")),
                new BillboardRoom(ROOM, "Synthetic Room", 80),
                new BillboardShowtime(SHOWTIME, STARTS, ENDS, 1500, "es", "en"));
    }

    private static BillboardEntry bareEntry() {
        return new BillboardEntry(
                new BillboardMovie(MOVIE, "Synthetic Movie", null, 120, null, "PG-13", null, null),
                new BillboardRoom(ROOM, "Synthetic Room", 80),
                new BillboardShowtime(SHOWTIME, STARTS, ENDS, 0, null, null));
    }

    private void billboardIs(BillboardEntry... entries) {
        when(useCases.getBillboard(any())).thenReturn(new BillboardPage(List.of(entries), 1, 20, entries.length));
    }

    private BillboardQuery queryOf(String url) throws Exception {
        mvc.perform(get(url));
        ArgumentCaptor<BillboardQuery> query = ArgumentCaptor.forClass(BillboardQuery.class);
        verify(useCases).getBillboard(query.capture());
        return query.getValue();
    }

    @Test
    void theBillboardAnswers200WithEveryPublicFieldOfTheMovieTheRoomAndTheShowtime() throws Exception {
        billboardIs(entry());

        mvc.perform(get("/billboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].movie.id").value(MOVIE.toString()))
                .andExpect(jsonPath("$.data[0].movie.title").value("Synthetic Movie"))
                .andExpect(jsonPath("$.data[0].movie.synopsis").value("A synopsis"))
                .andExpect(jsonPath("$.data[0].movie.durationMinutes").value(120))
                .andExpect(jsonPath("$.data[0].movie.genres[0]").value("Drama"))
                .andExpect(jsonPath("$.data[0].movie.genres[1]").value("Comedy"))
                .andExpect(jsonPath("$.data[0].movie.classification").value("PG-13"))
                .andExpect(jsonPath("$.data[0].movie.posterUrl").value("https://example.com/poster.png"))
                .andExpect(jsonPath("$.data[0].movie.releaseDate").value("2026-12-01"))
                .andExpect(jsonPath("$.data[0].room.id").value(ROOM.toString()))
                .andExpect(jsonPath("$.data[0].room.name").value("Synthetic Room"))
                .andExpect(jsonPath("$.data[0].room.capacity").value(80))
                .andExpect(jsonPath("$.data[0].showtime.id").value(SHOWTIME.toString()))
                .andExpect(jsonPath("$.data[0].showtime.startsAt").value("2026-11-01T20:00:00Z"))
                .andExpect(jsonPath("$.data[0].showtime.endsAt").value("2026-11-01T22:00:00Z"))
                .andExpect(jsonPath("$.data[0].showtime.priceCents").value(1500))
                .andExpect(jsonPath("$.data[0].showtime.audioLanguage").value("es"))
                .andExpect(jsonPath("$.data[0].showtime.subtitleLanguage").value("en"));
    }

    @Test
    void theBillboardExposesNoStatusCastSnapshotsOrSeatLayout() throws Exception {
        billboardIs(entry());

        mvc.perform(get("/billboard"))
                .andExpect(jsonPath("$.data[0].movie.status").doesNotExist())
                .andExpect(jsonPath("$.data[0].movie.cast").doesNotExist())
                .andExpect(jsonPath("$.data[0].room.seats").doesNotExist())
                .andExpect(jsonPath("$.data[0].showtime.status").doesNotExist())
                .andExpect(jsonPath("$.data[0].showtime.movieId").doesNotExist())
                .andExpect(jsonPath("$.data[0].showtime.roomId").doesNotExist());
    }

    @Test
    void theOptionalFieldsAreLeftOutWhenTheyHaveNoValue() throws Exception {
        billboardIs(bareEntry());

        mvc.perform(get("/billboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].movie.title").value("Synthetic Movie"))
                .andExpect(jsonPath("$.data[0].movie.synopsis").doesNotExist())
                .andExpect(jsonPath("$.data[0].movie.genres").doesNotExist())
                .andExpect(jsonPath("$.data[0].movie.posterUrl").doesNotExist())
                .andExpect(jsonPath("$.data[0].movie.releaseDate").doesNotExist())
                .andExpect(jsonPath("$.data[0].showtime.priceCents").value(0))
                .andExpect(jsonPath("$.data[0].showtime.audioLanguage").doesNotExist())
                .andExpect(jsonPath("$.data[0].showtime.subtitleLanguage").doesNotExist());
    }

    @Test
    void thePosterIsRenderedAsText() throws Exception {
        billboardIs(entry());

        mvc.perform(get("/billboard"))
                .andExpect(jsonPath("$.data[0].movie.posterUrl").isString());
    }

    @Test
    void anEmptyBillboardIsA200WithAnEmptyList() throws Exception {
        when(useCases.getBillboard(any())).thenReturn(new BillboardPage(List.of(), 1, 20, 0));

        mvc.perform(get("/billboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void theMetadataDescribesTheWindowTheUseCaseAnswered() throws Exception {
        when(useCases.getBillboard(any())).thenReturn(new BillboardPage(List.of(entry()), 2, 2, 3));

        mvc.perform(get("/billboard?page=2&limit=2"))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void withoutParametersTheFirstPageOfTwentyWithNoFilterIsAsked() throws Exception {
        billboardIs(entry());

        assertEquals(new BillboardQuery(null, null, 1, 20), queryOf("/billboard"));
    }

    @Test
    void theDateTheMovieAndTheWindowOfTheRequestReachTheUseCaseParsed() throws Exception {
        billboardIs(entry());

        assertEquals(new BillboardQuery(LocalDate.parse("2026-11-01"), MOVIE, 3, 50),
                queryOf("/billboard?date=2026-11-01&movieId=" + MOVIE + "&page=3&limit=50"));
    }

    @Test
    void theLimitMayBeTheMaximum() throws Exception {
        billboardIs(entry());

        mvc.perform(get("/billboard?limit=100")).andExpect(status().isOk());
        ArgumentCaptor<BillboardQuery> query = ArgumentCaptor.forClass(BillboardQuery.class);
        verify(useCases).getBillboard(query.capture());
        assertEquals(100, query.getValue().limit());
    }

    @ParameterizedTest
    @ValueSource(strings = {"date=2026-13-01", "date=01-11-2026", "date=2026-11-01T20:00:00Z", "date=tomorrow",
            "movieId=not-a-uuid", "page=first", "limit=many", "page=0", "page=-1", "limit=0", "limit=101"})
    void aMalformedOrOutOfBoundsParameterIsA400BeforeTheUseCaseIsAsked(String parameter) throws Exception {
        mvc.perform(get("/billboard?" + parameter))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").exists());

        verifyNoInteractions(useCases);
    }
}
