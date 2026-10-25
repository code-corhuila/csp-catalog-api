package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Contract tests of the movie routes: the stack, the validation against the contract and the guard of the environment are in {@link ContractTest}. */
@SpringBootTest
@AutoConfigureMockMvc
class MovieRoutesContractTest extends ContractTest {

    private static final String CREATE = """
            {"title":"Synthetic Movie","synopsis":"A synopsis","durationMinutes":120,"classification":"PG-13",
             "releaseDate":"2026-12-01","genres":["Drama"],"cast":["Synthetic Actor"],
             "posterUrl":"https://example.com/poster.png","status":"DRAFT"}""";

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return authorized(request, List.of("catalog:read", "catalog:write"));
    }

    private MockHttpServletRequestBuilder create(String key, String body) {
        return admin(post(BASE + "/movies")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void theLifeOfAMovieThroughEveryRouteMatchesTheContract() throws Exception {
        String creationKey = key();
        MvcResult created = call(create(creationKey, CREATE), 201);
        String id = body(created).get("id").asText();
        assertEquals("/api/v1/catalog/movies/" + id, created.getResponse().getHeader("Location"));
        assertEquals("DRAFT", body(created).get("status").asText());

        assertEquals(id, body(call(admin(get(BASE + "/movies/" + id)), 200)).get("id").asText());
        JsonNode list = body(call(admin(get(BASE + "/movies?page=1&limit=20")), 200));
        assertEquals(1, list.get("meta").get("total").asInt());
        assertEquals(1, list.get("meta").get("totalPages").asInt());
        assertEquals(id, list.get("data").get(0).get("id").asText());

        String publishKey = key();
        String publish = "{\"title\":\"New title\",\"status\":\"PUBLISHED\"}";
        JsonNode published = body(call(admin(patch(BASE + "/movies/" + id)).header("Idempotency-Key", publishKey)
                .contentType(MediaType.APPLICATION_JSON).content(publish), 200));
        assertEquals("PUBLISHED", published.get("status").asText());
        assertEquals("New title", published.get("title").asText());

        assertEquals(id, body(call(create(creationKey, CREATE), 201)).get("id").asText());
        assertEquals(1, body(call(admin(get(BASE + "/movies")), 200)).get("meta").get("total").asInt());

        String archiveKey = key();
        call(admin(delete(BASE + "/movies/" + id)).header("Idempotency-Key", archiveKey), 204);
        assertEquals("ARCHIVED", body(call(admin(get(BASE + "/movies/" + id)), 200)).get("status").asText());
        assertEquals(0, body(call(admin(get(BASE + "/movies")), 200)).get("meta").get("total").asInt());
        call(admin(delete(BASE + "/movies/" + id)).header("Idempotency-Key", archiveKey), 204);
        call(admin(patch(BASE + "/movies/" + id)).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Late change\"}"), 422);
    }

    @Test
    void everyErrorAnswerMatchesTheContract() throws Exception {
        callWithInvalidRequest(create(key(), CREATE.replace("\"durationMinutes\":120", "\"durationMinutes\":0")), 400);
        callWithInvalidRequest(admin(get(BASE + "/movies?limit=101")), 400);
        call(admin(get(BASE + "/movies/" + UUID.randomUUID())), 404);

        String sharedKey = key();
        call(create(sharedKey, CREATE), 201);
        call(create(sharedKey, CREATE.replace("Synthetic Movie", "Another Movie")), 409);

        String id = body(call(create(key(), CREATE.replace("\"DRAFT\"", "\"PUBLISHED\"")), 201)).get("id").asText();
        call(admin(patch(BASE + "/movies/" + id)).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DRAFT\"}"), 422);
    }

    @Test
    void theSecurityAnswersMatchTheContract() throws Exception {
        call(post(BASE + "/movies").contextPath(BASE).header("Authorization", "Bearer not-a-jwt")
                .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(CREATE), 401);
        call(authorized(post(BASE + "/movies"), List.of("catalog:read")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(CREATE), 403);
        call(authorized(get(BASE + "/movies"), List.of("catalog:read")), 403);
        call(get(BASE + "/movies/" + UUID.randomUUID()).contextPath(BASE).header("Authorization", "Bearer not-a-jwt"), 401);
    }
}
