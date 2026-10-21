package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Contract tests of the public billboard (HU-CATALOG-004, ADR-041): the movie, the room and the showtimes are
 * created through their own routes with their admin tokens, and the billboard is then read without any token.
 * The dates are relative to today so the showtimes stay upcoming (or past) whenever the test runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BillboardRoutesContractTest extends ContractTest {

    private static final String MOVIE = """
            {"title":"%s","synopsis":"A synopsis","durationMinutes":120,"classification":"PG-13",
             "releaseDate":"2026-12-01","genres":["Drama"],"cast":["Synthetic Actor"],
             "posterUrl":"https://example.com/poster.png","status":"%s"}""";
    private static final String ROOM = """
            {"name":"%s","seats":[{"label":"A1","row":"A","number":1,"type":"STANDARD"},{"label":"A2","type":"VIP"}]}""";
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final LocalDate DAY_ONE = TODAY.plusDays(30);
    private static final LocalDate DAY_TWO = TODAY.plusDays(31);

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request, String permission) {
        return authorized(request, List.of(permission));
    }

    private String movie(String title, String status) throws Exception {
        return body(call(admin(post(BASE + "/movies"), "catalog:write").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(MOVIE.formatted(title, status)), 201)).get("id").asText();
    }

    private String room(String name) throws Exception {
        return body(call(admin(post(BASE + "/rooms"), "room:write").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(ROOM.formatted(name)), 201)).get("id").asText();
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    private String showtime(String movieId, String roomId, Instant startsAt, Instant endsAt) throws Exception {
        String body = """
                {"movieId":"%s","roomId":"%s","startsAt":"%s","endsAt":"%s","priceCents":1500,
                 "audioLanguage":"es","subtitleLanguage":"en"}""".formatted(movieId, roomId, startsAt, endsAt);
        return body(call(admin(post(BASE + "/showtimes"), "showtime:write").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body), 201)).get("id").asText();
    }

    private String showtime(String movieId, String roomId, LocalDate day, int hour) throws Exception {
        return showtime(movieId, roomId, at(day, hour), at(day, hour).plus(2, ChronoUnit.HOURS));
    }

    private static MockHttpServletRequestBuilder billboard(String query) {
        return get(BASE + "/billboard" + query).contextPath(BASE);
    }

    private JsonNode billboardOf(String query) throws Exception {
        return body(call(billboard(query), 200));
    }

    private static List<String> showtimeIds(JsonNode billboard) {
        List<String> ids = new ArrayList<>();
        billboard.get("data").forEach(item -> ids.add(item.get("showtime").get("id").asText()));
        return ids;
    }

    @Test
    void aPublishedMovieWithAnUpcomingShowtimeAppearsWithItsMovieRoomAndShowtimeAndNeedsNoToken() throws Exception {
        String movieId = movie("Synthetic Movie", "PUBLISHED");
        String roomId = room("Synthetic Room");
        String showtimeId = showtime(movieId, roomId, DAY_ONE, 20);

        JsonNode billboard = billboardOf("");

        assertEquals(1, billboard.get("data").size());
        JsonNode item = billboard.get("data").get(0);
        assertEquals(movieId, item.get("movie").get("id").asText());
        assertEquals("Synthetic Movie", item.get("movie").get("title").asText());
        assertEquals(120, item.get("movie").get("durationMinutes").asInt());
        assertEquals("PG-13", item.get("movie").get("classification").asText());
        assertEquals("https://example.com/poster.png", item.get("movie").get("posterUrl").asText());
        assertFalse(item.get("movie").has("status"));
        assertFalse(item.get("movie").has("cast"));
        assertEquals(roomId, item.get("room").get("id").asText());
        assertEquals("Synthetic Room", item.get("room").get("name").asText());
        assertEquals(2, item.get("room").get("capacity").asInt());
        assertFalse(item.get("room").has("seats"));
        assertEquals(showtimeId, item.get("showtime").get("id").asText());
        assertEquals(at(DAY_ONE, 20).toString(), item.get("showtime").get("startsAt").asText());
        assertEquals(at(DAY_ONE, 22).toString(), item.get("showtime").get("endsAt").asText());
        assertEquals(1500, item.get("showtime").get("priceCents").asLong());
        assertEquals(1, billboard.get("meta").get("total").asInt());
    }

    @Test
    void aDraftMovieAndAMovieArchivedAfterwardsDoNotAppear() throws Exception {
        String draft = movie("Draft Movie", "DRAFT");
        String archived = movie("Archived Movie", "PUBLISHED");
        String roomId = room("Synthetic Room");
        showtime(archived, roomId, DAY_ONE, 18);
        call(admin(delete(BASE + "/movies/" + archived), "catalog:write").header("Idempotency-Key", key()), 204);
        String published = movie("Published Movie", "PUBLISHED");
        String visible = showtime(published, roomId, DAY_ONE, 21);

        assertEquals(List.of(visible), showtimeIds(billboardOf("")));
        assertEquals(0, billboardOf("?movieId=" + draft).get("data").size());
        assertEquals(0, billboardOf("?movieId=" + archived).get("data").size());
    }

    @Test
    void aDraftMovieCannotBeScheduledSoItNeverReachesTheBillboardThroughAShowtime() throws Exception {
        String draft = movie("Draft Movie", "DRAFT");
        String roomId = room("Synthetic Room");
        String body = """
                {"movieId":"%s","roomId":"%s","startsAt":"%s","endsAt":"%s","priceCents":1500}"""
                .formatted(draft, roomId, at(DAY_ONE, 20), at(DAY_ONE, 22));

        call(admin(post(BASE + "/showtimes"), "showtime:write").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body), 422);

        assertEquals(0, billboardOf("").get("data").size());
    }

    @Test
    void aShowtimeThatAlreadyEndedAndACancelledOneDoNotAppear() throws Exception {
        String movieId = movie("Synthetic Movie", "PUBLISHED");
        String roomId = room("Synthetic Room");
        showtime(movieId, roomId, TODAY.minusDays(3), 18);
        String cancelled = showtime(movieId, roomId, DAY_ONE, 18);
        call(admin(delete(BASE + "/showtimes/" + cancelled), "showtime:write").header("Idempotency-Key", key()), 204);
        String upcoming = showtime(movieId, roomId, DAY_ONE, 21);

        assertEquals(List.of(upcoming), showtimeIds(billboardOf("")));
    }

    @Test
    void anArchivedRoomHidesItsShowtimes() throws Exception {
        String movieId = movie("Synthetic Movie", "PUBLISHED");
        String archivedRoom = room("Archived Room");
        String openRoom = room("Open Room");
        showtime(movieId, archivedRoom, DAY_ONE, 20);
        String visible = showtime(movieId, openRoom, DAY_ONE, 20);
        call(admin(delete(BASE + "/rooms/" + archivedRoom), "room:write").header("Idempotency-Key", key()), 204);

        assertEquals(List.of(visible), showtimeIds(billboardOf("")));
    }

    @Test
    void theDateAndTheMovieFiltersKeepWhatMatchesAndCombineWithAnd() throws Exception {
        String first = movie("First Movie", "PUBLISHED");
        String second = movie("Second Movie", "PUBLISHED");
        String roomId = room("Synthetic Room");
        String firstDayOne = showtime(first, roomId, DAY_ONE, 16);
        String secondDayOne = showtime(second, roomId, DAY_ONE, 19);
        String firstDayTwo = showtime(first, roomId, DAY_TWO, 16);

        assertEquals(List.of(firstDayOne, secondDayOne, firstDayTwo), showtimeIds(billboardOf("")));
        assertEquals(List.of(firstDayOne, secondDayOne), showtimeIds(billboardOf("?date=" + DAY_ONE)));
        assertEquals(List.of(firstDayTwo), showtimeIds(billboardOf("?date=" + DAY_TWO)));
        assertEquals(List.of(firstDayOne, firstDayTwo), showtimeIds(billboardOf("?movieId=" + first)));
        assertEquals(List.of(secondDayOne), showtimeIds(billboardOf("?date=" + DAY_ONE + "&movieId=" + second)));
        assertEquals(0, billboardOf("?date=" + DAY_TWO + "&movieId=" + second).get("data").size());
    }

    @Test
    void theBillboardIsPaginatedAndItsMetadataCountsShowtimes() throws Exception {
        String movieId = movie("Synthetic Movie", "PUBLISHED");
        String roomId = room("Synthetic Room");
        String one = showtime(movieId, roomId, DAY_ONE, 14);
        String two = showtime(movieId, roomId, DAY_ONE, 17);
        String three = showtime(movieId, roomId, DAY_ONE, 20);

        JsonNode pageOne = billboardOf("?page=1&limit=2");
        JsonNode pageTwo = billboardOf("?page=2&limit=2");

        assertEquals(List.of(one, two), showtimeIds(pageOne));
        assertEquals(List.of(three), showtimeIds(pageTwo));
        assertEquals(1, pageOne.get("meta").get("page").asInt());
        assertEquals(2, pageOne.get("meta").get("limit").asInt());
        assertEquals(3, pageOne.get("meta").get("total").asInt());
        assertEquals(2, pageOne.get("meta").get("totalPages").asInt());
        assertEquals(2, pageTwo.get("meta").get("page").asInt());
        assertEquals(3, pageTwo.get("meta").get("total").asInt());
    }

    @Test
    void anEmptyBillboardIsA200WithAnEmptyList() throws Exception {
        JsonNode billboard = billboardOf("");

        assertEquals(0, billboard.get("data").size());
        assertEquals(0, billboard.get("meta").get("total").asInt());
    }

    @Test
    void aMalformedParameterIsA400ThatMatchesTheContract() throws Exception {
        callWithInvalidRequest(billboard("?date=2026-13-45"), 400);
        callWithInvalidRequest(billboard("?date=tomorrow"), 400);
        callWithInvalidRequest(billboard("?movieId=not-a-uuid"), 400);
        callWithInvalidRequest(billboard("?page=0"), 400);
        callWithInvalidRequest(billboard("?page=first"), 400);
        callWithInvalidRequest(billboard("?limit=0"), 400);
        callWithInvalidRequest(billboard("?limit=101"), 400);
    }

    @Test
    void aTokenThatIsSentIsIgnoredEvenWhenItIsInvalid() throws Exception {
        String movieId = movie("Synthetic Movie", "PUBLISHED");
        String roomId = room("Synthetic Room");
        String showtimeId = showtime(movieId, roomId, DAY_ONE, 20);

        JsonNode billboard = body(call(billboard("").header("Authorization", "Bearer not-a-jwt"), 200));
        JsonNode withoutPermission = body(call(authorized(get(BASE + "/billboard"), List.of("catalog:read")), 200));

        assertEquals(List.of(showtimeId), showtimeIds(billboard));
        assertEquals(List.of(showtimeId), showtimeIds(withoutPermission));
    }

    @Test
    void theAdminRoutesStillAnswer401WithoutAToken() throws Exception {
        call(get(BASE + "/movies").contextPath(BASE), 401);
        call(get(BASE + "/rooms").contextPath(BASE), 401);
        call(get(BASE + "/showtimes").contextPath(BASE), 401);
        call(get(BASE + "/showtimes/" + UUID.randomUUID()).contextPath(BASE), 401);
    }
}
