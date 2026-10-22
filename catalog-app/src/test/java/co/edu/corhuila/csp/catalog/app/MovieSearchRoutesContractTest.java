package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Contract tests of the public movie search (HU-CATALOG-006, ADR-044): the movies are created through their own
 * route with the admin token, and the search is then read without any token. Request and answer are validated
 * against the official contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MovieSearchRoutesContractTest extends ContractTest {

    private static final String MOVIE = """
            {"title":"%s","synopsis":"%s","durationMinutes":%d,"classification":"PG-13",
             "releaseDate":"2026-12-01","genres":[%s],"cast":["Synthetic Actor"],
             "posterUrl":"https://example.com/poster.png","status":"%s"}""";

    private String movie(String title, String synopsis, int minutes, String genres, String status) throws Exception {
        String body = MOVIE.formatted(title, synopsis, minutes, genres, status);
        return body(call(authorized(post(BASE + "/movies"), List.of("catalog:write")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body), 201)).get("id").asText();
    }

    private String published(String title, String synopsis, int minutes, String genres) throws Exception {
        return movie(title, synopsis, minutes, genres, "PUBLISHED");
    }

    private static MockHttpServletRequestBuilder search(String query) {
        return get(URI.create(BASE + "/billboard/movies" + query)).contextPath(BASE);
    }

    private JsonNode searchOf(String query) throws Exception {
        return body(call(search(query), 200));
    }

    private static List<String> ids(JsonNode result) {
        List<String> ids = new ArrayList<>();
        result.get("data").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    @Test
    void aPublishedMovieAppearsWithItsPublicFieldsAndNeedsNoToken() throws Exception {
        String id = published("Synthetic Movie", "A synthetic synopsis.", 120, "\"Drama\",\"Comedy\"");

        JsonNode result = searchOf("");

        assertEquals(List.of(id), ids(result));
        JsonNode item = result.get("data").get(0);
        assertEquals("Synthetic Movie", item.get("title").asText());
        assertEquals("A synthetic synopsis.", item.get("synopsis").asText());
        assertEquals(120, item.get("durationMinutes").asInt());
        assertEquals("PG-13", item.get("classification").asText());
        assertEquals("Drama", item.get("genres").get(0).asText());
        assertEquals("https://example.com/poster.png", item.get("posterUrl").asText());
        assertEquals("2026-12-01", item.get("releaseDate").asText());
        assertFalse(item.has("status"));
        assertFalse(item.has("cast"));
        assertEquals(1, result.get("meta").get("total").asInt());
    }

    @Test
    void aMovieWithNoShowtimeIsFoundAndADraftOrArchivedMovieIsNot() throws Exception {
        String visible = published("Visible Movie", "Shown.", 100, "\"Drama\"");
        movie("Draft Movie", "Not published.", 100, "\"Drama\"", "DRAFT");
        String archived = published("Archived Movie", "No longer shown.", 100, "\"Drama\"");
        call(authorized(delete(BASE + "/movies/" + archived), List.of("catalog:write")).header("Idempotency-Key", key()), 204);

        assertEquals(List.of(visible), ids(searchOf("")));
        assertEquals(List.of(visible), ids(searchOf("?q=movie")));
    }

    @Test
    void theTextFindsAWholeWordOfTheTitleOrTheSynopsisAndRequiresEveryWord() throws Exception {
        String action = published("Space Action", "A pilot crosses the galaxy.", 110, "\"Drama\"");
        String garden = published("Quiet Garden", "An action of kindness in a garden.", 95, "\"Drama\"");
        String quest = published("Galaxy Quest", "A crew in deep space.", 150, "\"Drama\"");

        assertEquals(List.of(garden, action), ids(searchOf("?q=action")));
        assertEquals(List.of(quest, action), ids(searchOf("?q=space%20galaxy")));
        assertEquals(0, searchOf("?q=act").get("data").size());
        assertEquals(0, searchOf("?q=space%20garden").get("data").size());
        assertEquals(List.of(garden, action), ids(searchOf("?q=-action")));
    }

    @Test
    void theGenreIsTheExactStoredValueAndTheDurationIsInclusive() throws Exception {
        String shortOne = published("Short Space", "A short one.", 90, "\"Ciencia ficción\"");
        String exact = published("Exact Space", "An exact one.", 120, "\"Ciencia ficción\",\"Acción\"");
        published("Long Space", "A long one.", 150, "\"Ciencia ficción\"");
        published("Quiet Garden", "A garden.", 100, "\"Drama\"");

        assertEquals(3, searchOf("?genre=Ciencia%20ficci%C3%B3n").get("data").size());
        assertEquals(0, searchOf("?genre=ciencia%20ficcion").get("data").size());
        assertEquals(List.of(exact, shortOne), ids(searchOf("?genre=Ciencia%20ficci%C3%B3n&durationMax=120")));
        assertEquals(0, searchOf("?durationMax=89").get("data").size());
    }

    @Test
    void theFiltersCombineWithAndAndTheListIsPaginatedByMovie() throws Exception {
        String a = published("Space A", "A.", 100, "\"Ciencia ficción\"");
        String b = published("Space B", "B.", 100, "\"Ciencia ficción\"");
        String c = published("Space C", "C.", 100, "\"Ciencia ficción\"");
        published("Space D", "D.", 200, "\"Ciencia ficción\"");

        JsonNode pageOne = searchOf("?q=space&genre=Ciencia%20ficci%C3%B3n&durationMax=150&page=1&limit=2");
        JsonNode pageTwo = searchOf("?q=space&genre=Ciencia%20ficci%C3%B3n&durationMax=150&page=2&limit=2");

        assertEquals(List.of(a, b), ids(pageOne));
        assertEquals(List.of(c), ids(pageTwo));
        assertEquals(1, pageOne.get("meta").get("page").asInt());
        assertEquals(2, pageOne.get("meta").get("limit").asInt());
        assertEquals(3, pageOne.get("meta").get("total").asInt());
        assertEquals(2, pageOne.get("meta").get("totalPages").asInt());
        assertEquals(2, pageTwo.get("meta").get("page").asInt());
    }

    @Test
    void anEmptySearchIsA200WithAnEmptyList() throws Exception {
        JsonNode result = searchOf("?q=nothing");

        assertEquals(0, result.get("data").size());
        assertEquals(0, result.get("meta").get("total").asInt());
    }

    @Test
    void aMalformedParameterIsA400ThatMatchesTheContract() throws Exception {
        callWithInvalidRequest(search("?durationMax=long"), 400);
        callWithInvalidRequest(search("?durationMax=0"), 400);
        callWithInvalidRequest(search("?genre="), 400);
        callWithInvalidRequest(search("?genre=" + "g".repeat(51)), 400);
        callWithInvalidRequest(search("?q=" + "x".repeat(101)), 400);
        callWithInvalidRequest(search("?page=0"), 400);
        callWithInvalidRequest(search("?page=first"), 400);
        callWithInvalidRequest(search("?limit=0"), 400);
        callWithInvalidRequest(search("?limit=101"), 400);
    }

    @Test
    void aTokenThatIsSentIsIgnoredEvenWhenItIsInvalid() throws Exception {
        String id = published("Synthetic Movie", "A synopsis.", 120, "\"Drama\"");

        JsonNode invalid = body(call(search("").header("Authorization", "Bearer not-a-jwt"), 200));
        JsonNode withoutPermission = body(call(authorized(get(BASE + "/billboard/movies"), List.of("catalog:read")), 200));

        assertEquals(List.of(id), ids(invalid));
        assertEquals(List.of(id), ids(withoutPermission));
    }

    @Test
    void theMovieRoutesOfTheAdministrationStillNeedTheirToken() throws Exception {
        call(get(BASE + "/movies").contextPath(BASE), 401);
        call(authorized(get(BASE + "/movies"), List.of("catalog:read")), 403);
        call(get(BASE + "/movies/" + java.util.UUID.randomUUID()).contextPath(BASE), 401);
    }
}
