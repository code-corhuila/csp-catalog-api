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

import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchQuery;
import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardMovie;
import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchPage;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The public movie search route: status, DTOs, window metadata and the query handed to the use case (ADR-044). */
class MovieSearchControllerTest {

    private static final UUID MOVIE = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final MovieSearchUseCases useCases = mock(MovieSearchUseCases.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MovieSearchController(useCases))
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                    .findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .addFilters(new CorrelationIdFilter())
            .build();

    private static BillboardMovie movie() {
        return new BillboardMovie(MOVIE, "Synthetic Movie", "A synopsis", 120, List.of("Drama", "Comedy"), "PG-13",
                URI.create("https://example.com/poster.png"), LocalDate.parse("2026-12-01"));
    }

    private static BillboardMovie bareMovie() {
        return new BillboardMovie(MOVIE, "Synthetic Movie", null, 120, null, "PG-13", null, null);
    }

    private void searchFinds(BillboardMovie... movies) {
        when(useCases.search(any())).thenReturn(new MovieSearchPage(List.of(movies), 1, 20, movies.length));
    }

    private MovieSearchQuery queryOf(String url) throws Exception {
        return queryOf(get(url));
    }

    private MovieSearchQuery queryOf(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request);
        ArgumentCaptor<MovieSearchQuery> query = ArgumentCaptor.forClass(MovieSearchQuery.class);
        verify(useCases).search(query.capture());
        return query.getValue();
    }

    @Test
    void theSearchAnswers200WithEveryPublicFieldOfTheMovie() throws Exception {
        searchFinds(movie());

        mvc.perform(get("/billboard/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(MOVIE.toString()))
                .andExpect(jsonPath("$.data[0].title").value("Synthetic Movie"))
                .andExpect(jsonPath("$.data[0].synopsis").value("A synopsis"))
                .andExpect(jsonPath("$.data[0].durationMinutes").value(120))
                .andExpect(jsonPath("$.data[0].genres[0]").value("Drama"))
                .andExpect(jsonPath("$.data[0].genres[1]").value("Comedy"))
                .andExpect(jsonPath("$.data[0].classification").value("PG-13"))
                .andExpect(jsonPath("$.data[0].posterUrl").value("https://example.com/poster.png"))
                .andExpect(jsonPath("$.data[0].releaseDate").value("2026-12-01"));
    }

    @Test
    void theSearchExposesNoStatusCastOrTimestamps() throws Exception {
        searchFinds(movie());

        mvc.perform(get("/billboard/movies"))
                .andExpect(jsonPath("$.data[0].status").doesNotExist())
                .andExpect(jsonPath("$.data[0].publicationStatus").doesNotExist())
                .andExpect(jsonPath("$.data[0].cast").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].deletedAt").doesNotExist());
    }

    @Test
    void theOptionalFieldsAreLeftOutWhenTheyHaveNoValue() throws Exception {
        searchFinds(bareMovie());

        mvc.perform(get("/billboard/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Synthetic Movie"))
                .andExpect(jsonPath("$.data[0].synopsis").doesNotExist())
                .andExpect(jsonPath("$.data[0].genres").doesNotExist())
                .andExpect(jsonPath("$.data[0].posterUrl").doesNotExist())
                .andExpect(jsonPath("$.data[0].releaseDate").doesNotExist());
    }

    @Test
    void anEmptySearchIsA200WithAnEmptyList() throws Exception {
        when(useCases.search(any())).thenReturn(new MovieSearchPage(List.of(), 1, 20, 0));

        mvc.perform(get("/billboard/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void theMetadataDescribesTheWindowTheUseCaseAnswered() throws Exception {
        when(useCases.search(any())).thenReturn(new MovieSearchPage(List.of(movie()), 2, 2, 3));

        mvc.perform(get("/billboard/movies?page=2&limit=2"))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void withoutParametersTheFirstPageOfTwentyWithNoFilterIsAsked() throws Exception {
        searchFinds(movie());

        assertEquals(new MovieSearchQuery(null, null, null, 1, 20), queryOf("/billboard/movies"));
    }

    @Test
    void theFiltersAndTheWindowOfTheRequestReachTheUseCaseParsed() throws Exception {
        searchFinds(movie());

        assertEquals(new MovieSearchQuery("space action", "Ciencia ficción", 150, 3, 50),
                queryOf(get("/billboard/movies").param("q", "space action").param("genre", "Ciencia ficción")
                        .param("durationMax", "150").param("page", "3").param("limit", "50")));
    }

    @Test
    void aBlankTextIsIgnored() throws Exception {
        searchFinds(movie());

        assertEquals(new MovieSearchQuery(null, null, null, 1, 20), queryOf(get("/billboard/movies").param("q", "   ")));
    }

    @Test
    void theLimitMayBeTheMaximum() throws Exception {
        searchFinds(movie());

        assertEquals(100, queryOf("/billboard/movies?limit=100").limit());
    }

    @ParameterizedTest
    @ValueSource(strings = {"durationMax=long", "durationMax=0", "durationMax=-5", "durationMax=1.5", "genre=",
            "page=first", "limit=many", "page=0", "page=-1", "limit=0", "limit=101"})
    void aMalformedOrOutOfBoundsParameterIsA400BeforeTheUseCaseIsAsked(String parameter) throws Exception {
        mvc.perform(get("/billboard/movies?" + parameter))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").exists());

        verifyNoInteractions(useCases);
    }

    @Test
    void aBlankGenreIsA400BeforeTheUseCaseIsAsked() throws Exception {
        mvc.perform(get("/billboard/movies").param("genre", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(useCases);
    }

    @Test
    void aGenreOrATextThatIsTooLongIsA400BeforeTheUseCaseIsAsked() throws Exception {
        mvc.perform(get("/billboard/movies?genre=" + "g".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mvc.perform(get("/billboard/movies?q=" + "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(useCases);
    }
}
