package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Contract tests of the availability of a showtime for Booking (HU-CATALOG-005, ADR-042): the movie, the room
 * and the showtime are created through their own routes with their admin tokens, and the availability is then
 * read with the token of the service, through the real stack and MongoDB, against the official contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AvailabilityRoutesContractTest extends ContractTest {

    private static final String SERVICE = "booking-service";
    private static final String MOVIE = """
            {"title":"%s","synopsis":"A synopsis","durationMinutes":120,"classification":"PG-13",
             "releaseDate":"2026-12-01","genres":["Drama"],"cast":["Synthetic Actor"],
             "posterUrl":"https://example.com/poster.png","status":"PUBLISHED"}""";
    private static final String ROOM = """
            {"name":"%s","seats":[{"label":"A1","row":"A","number":1,"type":"STANDARD"},{"label":"A2","type":"VIP"},
                                  {"label":"B1","row":"B","number":1,"type":"ACCESSIBLE"}]}""";
    private static final LocalDate DAY = LocalDate.now(ZoneOffset.UTC).plusDays(30);
    private static final Instant STARTS = DAY.atTime(20, 0).toInstant(ZoneOffset.UTC);
    private static final Instant ENDS = STARTS.plus(2, ChronoUnit.HOURS);

    @Autowired
    private MongoTemplate mongo;

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request, String permission) {
        return authorized(request, List.of(permission));
    }

    private String movie(String title) throws Exception {
        return body(call(admin(post(BASE + "/movies"), "catalog:write").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(MOVIE.formatted(title)), 201)).get("id").asText();
    }

    private String room(String name) throws Exception {
        return body(call(admin(post(BASE + "/rooms"), "room:write").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(ROOM.formatted(name)), 201)).get("id").asText();
    }

    private String showtime(String movieId, String roomId) throws Exception {
        String body = """
                {"movieId":"%s","roomId":"%s","startsAt":"%s","endsAt":"%s","priceCents":1500,
                 "audioLanguage":"es","subtitleLanguage":"en"}""".formatted(movieId, roomId, STARTS, ENDS);
        return body(call(admin(post(BASE + "/showtimes"), "showtime:write").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body), 201)).get("id").asText();
    }

    private void change(String resource, String id, String permission, String body) throws Exception {
        call(admin(patch(BASE + "/" + resource + "/" + id), permission).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body), 200);
    }

    private void remove(String resource, String id, String permission) throws Exception {
        call(admin(delete(BASE + "/" + resource + "/" + id), permission).header("Idempotency-Key", key()), 204);
    }

    private static MockHttpServletRequestBuilder availability(String showtimeId, String token) {
        return get(BASE + "/showtimes/" + showtimeId + "/availability").contextPath(BASE)
                .header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder asService(String showtimeId) {
        return availability(showtimeId, RsaTestKey.token(SERVICE, List.of()));
    }

    private JsonNode availabilityOf(String showtimeId) throws Exception {
        return body(call(asService(showtimeId), 200));
    }

    private static List<String> labels(JsonNode availability) {
        List<String> labels = new ArrayList<>();
        availability.get("seats").forEach(seat -> labels.add(seat.asText()));
        return labels;
    }

    private List<Long> counts() {
        return List.of("movies", "rooms", "showtimes", "outbox_event", "idempotency_key").stream()
                .map(name -> mongo.getCollection(name).countDocuments()).toList();
    }

    @Test
    void aScheduledShowtimeOfAPublishedMovieAnswersItsSnapshotsDatesPriceAndSeats() throws Exception {
        String movieId = movie("Synthetic Movie");
        String roomId = room("Synthetic Room");
        String showtimeId = showtime(movieId, roomId);

        JsonNode answer = availabilityOf(showtimeId);

        assertEquals(8, answer.size());
        assertEquals(showtimeId, answer.get("showtimeId").asText());
        assertEquals("Synthetic Movie", answer.get("movieTitle").asText());
        assertEquals("Synthetic Room", answer.get("roomName").asText());
        assertEquals(STARTS.toString(), answer.get("startsAt").asText());
        assertEquals(ENDS.toString(), answer.get("endsAt").asText());
        assertTrue(answer.get("price").isIntegralNumber());
        assertEquals(1500, answer.get("price").asLong());
        assertEquals(List.of("A1", "A2", "B1"), labels(answer));
    }

    @Test
    void theSeatLayoutCarriesTheLabelTheRowTheNumberAndTheTypeOfEverySeatAndKeepsTheSeats() throws Exception {
        String showtimeId = showtime(movie("Synthetic Movie"), room("Synthetic Room"));

        JsonNode answer = availabilityOf(showtimeId);

        JsonNode layout = answer.get("seatLayout");
        assertEquals(3, layout.size());
        assertEquals(labels(answer), List.of(layout.get(0).get("label").asText(), layout.get(1).get("label").asText(),
                layout.get(2).get("label").asText()));
        assertEquals("A", layout.get(0).get("row").asText());
        assertEquals(1, layout.get(0).get("number").asInt());
        assertEquals("STANDARD", layout.get(0).get("type").asText());
        assertEquals("VIP", layout.get(1).get("type").asText());
        assertFalse(layout.get(1).has("row"));
        assertFalse(layout.get(1).has("number"));
        assertEquals("B", layout.get(2).get("row").asText());
        assertEquals("ACCESSIBLE", layout.get(2).get("type").asText());
    }

    @Test
    void theSnapshotsSurviveARenameOfTheMovieAndTheRoomButTheSeatLayoutIsTheCurrentOne() throws Exception {
        String movieId = movie("Synthetic Movie");
        String roomId = room("Synthetic Room");
        String showtimeId = showtime(movieId, roomId);

        change("movies", movieId, "catalog:write", "{\"title\":\"Renamed Movie\"}");
        change("rooms", roomId, "room:write", "{\"name\":\"Renamed Room\"}");
        JsonNode renamed = availabilityOf(showtimeId);
        change("rooms", roomId, "room:write", "{\"seats\":[{\"label\":\"C1\"},{\"label\":\"C2\"}]}");
        JsonNode relaid = availabilityOf(showtimeId);

        assertEquals("Synthetic Movie", renamed.get("movieTitle").asText());
        assertEquals("Synthetic Room", renamed.get("roomName").asText());
        assertEquals(List.of("A1", "A2", "B1"), labels(renamed));
        assertEquals("Synthetic Movie", relaid.get("movieTitle").asText());
        assertEquals("Synthetic Room", relaid.get("roomName").asText());
        assertEquals(List.of("C1", "C2"), labels(relaid));
    }

    @Test
    void anUnknownShowtimeIsA404OfTheContract() throws Exception {
        JsonNode error = body(call(asService(UUID.randomUUID().toString()), 404));

        assertEquals("NOT_FOUND", error.get("error").asText());
    }

    @Test
    void aCancelledShowtimeAnArchivedRoomAndAMovieArchivedAfterwardsAreA404() throws Exception {
        String movieId = movie("Synthetic Movie");
        String cancelled = showtime(movieId, room("Cancelled Room"));
        String archivedRoomId = room("Archived Room");
        String roomArchived = showtime(movieId, archivedRoomId);
        String archivedMovieId = movie("Archived Movie");
        String movieArchived = showtime(archivedMovieId, room("Other Room"));
        String available = showtime(movieId, room("Available Room"));

        remove("showtimes", cancelled, "showtime:write");
        remove("rooms", archivedRoomId, "room:write");
        remove("movies", archivedMovieId, "catalog:write");

        call(asService(cancelled), 404);
        call(asService(roomArchived), 404);
        call(asService(movieArchived), 404);
        call(asService(available), 200);
    }

    @Test
    void noTokenIsA401AndAnInvalidTokenIsA401() throws Exception {
        String showtimeId = showtime(movie("Synthetic Movie"), room("Synthetic Room"));

        JsonNode missing = body(callWithInvalidRequest(
                get(BASE + "/showtimes/" + showtimeId + "/availability").contextPath(BASE), 401));
        JsonNode invalid = body(call(availability(showtimeId, "not-a-jwt"), 401));

        assertEquals("UNAUTHORIZED", missing.get("error").asText());
        assertEquals("INVALID_TOKEN", invalid.get("error").asText());
    }

    @Test
    void anAdministratorTokenAndATokenOfAnotherSubjectAreA403() throws Exception {
        String showtimeId = showtime(movie("Synthetic Movie"), room("Synthetic Room"));
        List<String> administrator = List.of("showtime:write", "catalog:write", "room:write");

        JsonNode byAdministrator = body(call(availability(showtimeId, RsaTestKey.token(administrator)), 403));
        call(availability(showtimeId, RsaTestKey.token(List.of())), 403);
        call(availability(showtimeId, RsaTestKey.token("payment-service", administrator)), 403);
        call(availability(UUID.randomUUID().toString(), RsaTestKey.token(administrator)), 403);

        assertEquals("FORBIDDEN", byAdministrator.get("error").asText());
    }

    @Test
    void aServiceTokenOpensNoOtherRoute() throws Exception {
        String showtimeId = showtime(movie("Synthetic Movie"), room("Synthetic Room"));
        String service = RsaTestKey.token(SERVICE, List.of("showtime:write", "catalog:write", "room:write"));

        call(get(BASE + "/showtimes").contextPath(BASE).header("Authorization", "Bearer " + service), 403);
        call(get(BASE + "/showtimes/" + showtimeId).contextPath(BASE).header("Authorization", "Bearer " + service), 403);
        call(get(BASE + "/movies").contextPath(BASE).header("Authorization", "Bearer " + service), 403);
        call(get(BASE + "/rooms").contextPath(BASE).header("Authorization", "Bearer " + service), 403);
    }

    @Test
    void theRouteWritesNothing() throws Exception {
        String showtimeId = showtime(movie("Synthetic Movie"), room("Synthetic Room"));
        List<Long> before = counts();

        availabilityOf(showtimeId);
        call(asService(UUID.randomUUID().toString()), 404);
        call(availability(showtimeId, RsaTestKey.token(List.of())), 403);

        assertEquals(before, counts());
        assertEquals(1, mongo.getCollection("outbox_event").countDocuments());
    }
}
