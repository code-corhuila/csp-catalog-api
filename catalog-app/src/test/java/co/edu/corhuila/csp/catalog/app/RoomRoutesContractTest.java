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

/** Contract tests of the room routes: the stack and the validation against the contract are in {@link ContractTest}. */
@SpringBootTest
@AutoConfigureMockMvc
class RoomRoutesContractTest extends ContractTest {

    private static final String CREATE = """
            {"name":"Synthetic Room","capacity":2,
             "seats":[{"label":"A1","row":"A","number":1,"type":"STANDARD"},{"label":"A2","type":"VIP"}]}""";
    private static final String UPDATE = """
            {"name":"Renamed Room",
             "seats":[{"label":"A1","type":"VIP"},{"label":"B1","row":"B","number":1,"type":"ACCESSIBLE"},
                      {"label":"B2"}]}""";

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return authorized(request, List.of("room:write"));
    }

    private MockHttpServletRequestBuilder create(String key, String body) {
        return admin(post(BASE + "/rooms")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder update(String id, String key, String body) {
        return admin(patch(BASE + "/rooms/" + id)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void theLifeOfARoomThroughEveryRouteMatchesTheContract() throws Exception {
        String creationKey = key();
        MvcResult created = call(create(creationKey, CREATE), 201);
        String id = body(created).get("id").asText();
        assertEquals("/api/v1/catalog/rooms/" + id, created.getResponse().getHeader("Location"));
        assertEquals(2, body(created).get("capacity").asInt());
        assertEquals("STANDARD", body(created).get("seats").get(0).get("type").asText());

        JsonNode read = body(call(admin(get(BASE + "/rooms/" + id)), 200));
        assertEquals(id, read.get("id").asText());
        assertEquals("A1", read.get("seats").get(0).get("label").asText());
        assertEquals("VIP", read.get("seats").get(1).get("type").asText());
        assertEquals(false, read.get("seats").get(1).has("row"));
        JsonNode list = body(call(admin(get(BASE + "/rooms?page=1&limit=20")), 200));
        assertEquals(1, list.get("meta").get("total").asInt());
        assertEquals(1, list.get("meta").get("totalPages").asInt());
        assertEquals(id, list.get("data").get(0).get("id").asText());

        JsonNode updated = body(call(update(id, key(), UPDATE), 200));
        assertEquals("Renamed Room", updated.get("name").asText());
        assertEquals(3, updated.get("capacity").asInt());
        assertEquals("ACCESSIBLE", updated.get("seats").get(1).get("type").asText());

        assertEquals(id, body(call(create(creationKey, CREATE), 201)).get("id").asText());
        assertEquals(1, body(call(admin(get(BASE + "/rooms")), 200)).get("meta").get("total").asInt());

        String archiveKey = key();
        call(admin(delete(BASE + "/rooms/" + id)).header("Idempotency-Key", archiveKey), 204);
        call(admin(delete(BASE + "/rooms/" + id)).header("Idempotency-Key", archiveKey), 204);
        call(admin(get(BASE + "/rooms/" + id)), 404);
        call(update(id, key(), "{\"name\":\"Late change\"}"), 404);
        JsonNode empty = body(call(admin(get(BASE + "/rooms")), 200));
        assertEquals(0, empty.get("meta").get("total").asInt());
        assertEquals(0, empty.get("meta").get("totalPages").asInt());
        assertEquals(0, empty.get("data").size());
    }

    @Test
    void everyErrorAnswerMatchesTheContract() throws Exception {
        call(create(key(), "{\"name\":\"Twin Room\",\"seats\":[{\"label\":\"A1\"},{\"label\":\"A1\"}]}"), 400);
        call(create(key(), CREATE.replace("\"capacity\":2", "\"capacity\":3")), 400);
        callWithInvalidRequest(admin(get(BASE + "/rooms?limit=101")), 400);
        call(admin(get(BASE + "/rooms/" + UUID.randomUUID())), 404);
        call(admin(delete(BASE + "/rooms/" + UUID.randomUUID())).header("Idempotency-Key", key()), 404);

        String sharedKey = key();
        String id = body(call(create(sharedKey, CREATE), 201)).get("id").asText();
        call(create(sharedKey, CREATE.replace("Synthetic Room", "Another Room")), 409);
        call(create(key(), CREATE), 409);

        call(admin(delete(BASE + "/rooms/" + id)).header("Idempotency-Key", key()), 204);
        call(admin(delete(BASE + "/rooms/" + id)).header("Idempotency-Key", key()), 404);
    }

    @Test
    void theSecurityAnswersMatchTheContract() throws Exception {
        call(post(BASE + "/rooms").contextPath(BASE).header("Authorization", "Bearer not-a-jwt")
                .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(CREATE), 401);
        call(authorized(post(BASE + "/rooms"), List.of("catalog:write")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(CREATE), 403);
        call(authorized(get(BASE + "/rooms"), List.of("catalog:write")), 403);
        call(get(BASE + "/rooms/" + UUID.randomUUID()).contextPath(BASE).header("Authorization", "Bearer not-a-jwt"), 401);
    }
}
