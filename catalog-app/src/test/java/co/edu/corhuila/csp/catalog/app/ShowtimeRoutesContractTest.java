package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Contract tests of the showtime routes: the stack and the validation against the contract are in
 * {@link ContractTest}. The movie and the room of every showtime are created through their own routes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShowtimeRoutesContractTest extends ContractTest {

    private static final String MOVIE = """
            {"title":"Synthetic Movie","synopsis":"A synopsis","durationMinutes":120,"classification":"PG-13",
             "releaseDate":"2026-12-01","genres":["Drama"],"cast":["Synthetic Actor"],
             "posterUrl":"https://example.com/poster.png","status":"%s"}""";
    private static final String ROOM = """
            {"name":"%s","seats":[{"label":"A1","row":"A","number":1,"type":"STANDARD"},{"label":"A2","type":"VIP"}]}""";
    private static final String EIGHT_PM = "2027-03-05T20:00:00Z";
    private static final String NINE_PM = "2027-03-05T21:00:00Z";
    private static final String TEN_PM = "2027-03-05T22:00:00Z";
    private static final String ELEVEN_PM = "2027-03-05T23:00:00Z";
    private static final String MIDNIGHT = "2027-03-06T00:00:00Z";

    @Autowired
    private MongoTemplate mongo;

    private static MockHttpServletRequestBuilder movieAdmin(MockHttpServletRequestBuilder request) {
        return authorized(request, List.of("catalog:write"));
    }

    private static MockHttpServletRequestBuilder roomAdmin(MockHttpServletRequestBuilder request) {
        return authorized(request, List.of("room:write"));
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return authorized(request, List.of("showtime:write"));
    }

    private String movie(String status) throws Exception {
        return body(call(movieAdmin(post(BASE + "/movies")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(MOVIE.formatted(status)), 201)).get("id").asText();
    }

    private String room(String name) throws Exception {
        return body(call(roomAdmin(post(BASE + "/rooms")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(ROOM.formatted(name)), 201)).get("id").asText();
    }

    private static String showtime(String movieId, String roomId, String startsAt, String endsAt, long priceCents) {
        return """
                {"movieId":"%s","roomId":"%s","startsAt":"%s","endsAt":"%s","priceCents":%d,
                 "audioLanguage":"es","subtitleLanguage":"en"}""".formatted(movieId, roomId, startsAt, endsAt, priceCents);
    }

    private MockHttpServletRequestBuilder create(String key, String body) {
        return admin(post(BASE + "/showtimes")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder update(String id, String key, String body) {
        return admin(patch(BASE + "/showtimes/" + id)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder cancel(String id, String key) {
        return admin(delete(BASE + "/showtimes/" + id)).header("Idempotency-Key", key);
    }

    private long outboxEvents() {
        return mongo.getCollection("outbox_event").countDocuments();
    }

    @Test
    void theLifeOfAShowtimeThroughEveryRouteMatchesTheContract() throws Exception {
        String movieId = movie("PUBLISHED");
        String roomId = room("Synthetic Room");
        String creationKey = key();
        String creation = showtime(movieId, roomId, EIGHT_PM, TEN_PM, 1500);

        MvcResult created = call(create(creationKey, creation), 201);
        String id = body(created).get("id").asText();
        assertEquals("/api/v1/catalog/showtimes/" + id, created.getResponse().getHeader("Location"));
        assertEquals("SCHEDULED", body(created).get("status").asText());
        assertEquals(movieId, body(created).get("movieId").asText());
        assertEquals(roomId, body(created).get("roomId").asText());
        assertEquals(EIGHT_PM, body(created).get("startsAt").asText());
        assertEquals(TEN_PM, body(created).get("endsAt").asText());
        assertEquals(1500, body(created).get("priceCents").asLong());
        assertEquals("es", body(created).get("audioLanguage").asText());
        assertEquals("en", body(created).get("subtitleLanguage").asText());

        JsonNode read = body(call(admin(get(BASE + "/showtimes/" + id)), 200));
        assertEquals(id, read.get("id").asText());
        assertEquals(EIGHT_PM, read.get("startsAt").asText());
        JsonNode list = body(call(admin(get(BASE + "/showtimes?page=1&limit=20")), 200));
        assertEquals(1, list.get("meta").get("total").asInt());
        assertEquals(1, list.get("meta").get("totalPages").asInt());
        assertEquals(id, list.get("data").get(0).get("id").asText());

        JsonNode repriced = body(call(update(id, key(),
                "{\"priceCents\":2000,\"audioLanguage\":\"fr\",\"subtitleLanguage\":\"de\"}"), 200));
        assertEquals(2000, repriced.get("priceCents").asLong());
        assertEquals("fr", repriced.get("audioLanguage").asText());
        assertEquals("de", repriced.get("subtitleLanguage").asText());
        assertEquals(EIGHT_PM, repriced.get("startsAt").asText());

        JsonNode moved = body(call(update(id, key(),
                "{\"startsAt\":\"" + NINE_PM + "\",\"endsAt\":\"" + ELEVEN_PM + "\"}"), 200));
        assertEquals(NINE_PM, moved.get("startsAt").asText());
        assertEquals(ELEVEN_PM, moved.get("endsAt").asText());
        assertEquals(2000, moved.get("priceCents").asLong());

        assertEquals(id, body(call(create(creationKey, creation), 201)).get("id").asText());
        assertEquals(1, body(call(admin(get(BASE + "/showtimes")), 200)).get("meta").get("total").asInt());
        assertEquals(1, outboxEvents());

        String cancelKey = key();
        call(cancel(id, cancelKey), 204);
        call(cancel(id, cancelKey), 204);
        assertEquals("CANCELLED", body(call(admin(get(BASE + "/showtimes/" + id)), 200)).get("status").asText());
        call(update(id, key(), "{\"priceCents\":1}"), 422);
        assertEquals(1, body(call(admin(get(BASE + "/showtimes")), 200)).get("meta").get("total").asInt());
        assertEquals(1, outboxEvents());
    }

    @Test
    void aShowtimeInAFreeWindowIsScheduledAndPublishesExactlyOneEvent() throws Exception {
        String movieId = movie("PUBLISHED");
        String roomId = room("Synthetic Room");

        JsonNode created = body(call(create(key(), showtime(movieId, roomId, EIGHT_PM, TEN_PM, 1500)), 201));

        assertEquals("SCHEDULED", created.get("status").asText());
        assertEquals(1, outboxEvents());
        assertEquals(created.get("id").asText(),
                mongo.getCollection("outbox_event").find().first().getString("aggregate_id"));
    }

    @Test
    void aShowtimeThatOverlapsAnotherOfTheRoomIsA409AndOneThatTouchesItIsScheduled() throws Exception {
        String movieId = movie("PUBLISHED");
        String roomId = room("Synthetic Room");
        call(create(key(), showtime(movieId, roomId, EIGHT_PM, TEN_PM, 1500)), 201);

        call(create(key(), showtime(movieId, roomId, NINE_PM, ELEVEN_PM, 1500)), 409);
        assertEquals(1, outboxEvents());

        call(create(key(), showtime(movieId, roomId, TEN_PM, MIDNIGHT, 1500)), 201);
        assertEquals(2, outboxEvents());
        assertEquals(2, body(call(admin(get(BASE + "/showtimes")), 200)).get("meta").get("total").asInt());
    }

    @Test
    void movingAShowtimeOntoAnotherOneOfTheRoomIsA409() throws Exception {
        String movieId = movie("PUBLISHED");
        String roomId = room("Synthetic Room");
        call(create(key(), showtime(movieId, roomId, EIGHT_PM, TEN_PM, 1500)), 201);
        String later = body(call(create(key(), showtime(movieId, roomId, TEN_PM, MIDNIGHT, 1500)), 201))
                .get("id").asText();

        call(update(later, key(), "{\"startsAt\":\"" + NINE_PM + "\"}"), 409);
    }

    @Test
    void everyErrorAnswerMatchesTheContract() throws Exception {
        String movieId = movie("PUBLISHED");
        String roomId = room("Synthetic Room");

        call(create(key(), showtime(movieId, roomId, TEN_PM, EIGHT_PM, 1500)), 400);
        callWithInvalidRequest(create(key(), showtime(movieId, roomId, EIGHT_PM, TEN_PM, -1)), 400);
        callWithInvalidRequest(create(key(), "{\"movieId\":\"" + movieId + "\",\"roomId\":\"" + roomId + "\"}"), 400);
        callWithInvalidRequest(admin(get(BASE + "/showtimes?limit=101")), 400);

        call(admin(get(BASE + "/showtimes/" + UUID.randomUUID())), 404);
        call(update(UUID.randomUUID().toString(), key(), "{\"priceCents\":1}"), 404);
        call(cancel(UUID.randomUUID().toString(), key()), 404);

        String sharedKey = key();
        String id = body(call(create(sharedKey, showtime(movieId, roomId, EIGHT_PM, TEN_PM, 1500)), 201))
                .get("id").asText();
        call(create(sharedKey, showtime(movieId, roomId, EIGHT_PM, TEN_PM, 1600)), 409);
        String updateKey = key();
        call(update(id, updateKey, "{\"priceCents\":1700}"), 200);
        call(update(id, updateKey, "{\"priceCents\":1800}"), 409);
        callWithInvalidRequest(update(id, key(), "{\"priceCents\":-1}"), 400);
        call(update(id, key(), "{\"endsAt\":\"2027-03-05T19:00:00Z\"}"), 400);

        call(cancel(id, key()), 204);
        call(update(id, key(), "{\"status\":\"COMPLETED\"}"), 422);
        call(cancel(id, key()), 422);
        assertEquals(1, outboxEvents());
    }

    @Test
    void aMovieOrRoomThatCannotBeUsedIsA422AndNothingIsWritten() throws Exception {
        String roomId = room("Synthetic Room");
        String draft = movie("DRAFT");
        String archived = movie("PUBLISHED");
        call(movieAdmin(delete(BASE + "/movies/" + archived)).header("Idempotency-Key", key()), 204);
        String archivedRoom = room("Archived Room");
        call(roomAdmin(delete(BASE + "/rooms/" + archivedRoom)).header("Idempotency-Key", key()), 204);
        String published = movie("PUBLISHED");

        call(create(key(), showtime(draft, roomId, EIGHT_PM, TEN_PM, 1500)), 422);
        call(create(key(), showtime(archived, roomId, EIGHT_PM, TEN_PM, 1500)), 422);
        call(create(key(), showtime(UUID.randomUUID().toString(), roomId, EIGHT_PM, TEN_PM, 1500)), 422);
        call(create(key(), showtime(published, archivedRoom, EIGHT_PM, TEN_PM, 1500)), 422);
        call(create(key(), showtime(published, UUID.randomUUID().toString(), EIGHT_PM, TEN_PM, 1500)), 422);

        assertEquals(0, outboxEvents());
        assertEquals(0, body(call(admin(get(BASE + "/showtimes")), 200)).get("meta").get("total").asInt());
    }

    @Test
    void theSecurityAnswersMatchTheContract() throws Exception {
        String body = showtime(UUID.randomUUID().toString(), UUID.randomUUID().toString(), EIGHT_PM, TEN_PM, 1500);
        call(post(BASE + "/showtimes").contextPath(BASE).header("Authorization", "Bearer not-a-jwt")
                .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(body), 401);
        call(authorized(post(BASE + "/showtimes"), List.of("catalog:write", "room:write"))
                .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(body), 403);
        call(authorized(get(BASE + "/showtimes"), List.of("room:write")), 403);
        call(get(BASE + "/showtimes/" + UUID.randomUUID()).contextPath(BASE).header("Authorization", "Bearer not-a-jwt"),
                401);
        call(authorized(get(BASE + "/showtimes/" + UUID.randomUUID()), List.of("catalog:write")), 403);
        call(authorized(patch(BASE + "/showtimes/" + UUID.randomUUID()), List.of("room:write"))
                .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{}"), 403);
        call(authorized(delete(BASE + "/showtimes/" + UUID.randomUUID()), List.of("catalog:write"))
                .header("Idempotency-Key", key()), 403);
        assertFalse(outboxEvents() > 0);
    }
}
