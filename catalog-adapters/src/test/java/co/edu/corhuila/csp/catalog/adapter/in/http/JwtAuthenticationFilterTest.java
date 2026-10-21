package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The bearer token is validated by the service itself (Norma 5.3.7) and the permission of the route is
 * checked before any controller runs; a rejection answers the error envelope with the traceId of the request.
 */
class JwtAuthenticationFilterTest {

    private static final String TRACE = "550e8400-e29b-41d4-a716-446655440000";
    private static final List<String> WRITER = List.of("catalog:read", "catalog:write");
    private static final List<String> READER = List.of("catalog:read");
    private static final List<String> CATALOG_WRITER = List.of("catalog:write");
    private static final List<String> ROOM_WRITER = List.of("room:write");
    private static final List<String> SHOWTIME_WRITER = List.of("showtime:write");

    private final ObjectMapper json = new ObjectMapper();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(TestTokens.publicKey(), json);

    @BeforeEach
    void putTheTraceInTheLog() {
        MDC.put(CorrelationIdFilter.TRACE_ID_MDC_KEY, TRACE);
    }

    @AfterEach
    void clearTheLog() {
        MDC.clear();
    }

    private record Outcome(MockHttpServletRequest request, MockHttpServletResponse response, boolean chained) {
    }

    private Outcome call(String method, String path, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/catalog" + path);
        request.setContextPath("/api/v1/catalog");
        request.setServletPath(path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(request, response, chain.getRequest() != null);
    }

    private Outcome post(String path, String token) throws Exception {
        return call("POST", path, "Bearer " + token);
    }

    private JsonNode body(Outcome outcome) throws Exception {
        return json.readTree(outcome.response().getContentAsString());
    }

    private void assertRejected(Outcome outcome, int status, String error) throws Exception {
        assertFalse(outcome.chained(), "a rejected request must not reach a controller");
        assertEquals(status, outcome.response().getStatus());
        assertTrue(outcome.response().getContentType().startsWith("application/json"));
        JsonNode body = body(outcome);
        assertEquals(error, body.get("error").asText());
        assertEquals(TRACE, body.get("traceId").asText());
        assertTrue(body.has("message"));
    }

    @Test
    void aValidTokenWithTheWritePermissionReachesTheController() throws Exception {
        Outcome outcome = post("/movies", TestTokens.valid(WRITER));

        assertTrue(outcome.chained());
        assertEquals(200, outcome.response().getStatus());
        assertEquals(TestTokens.SUBJECT, outcome.request().getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/movies", "/movies/", "/movies/11111111-1111-4111-8111-111111111111"})
    void everyRouteOfTheMoviesNeedsTheWritePermissionAndReadsAreNoException(String path) throws Exception {
        assertTrue(call("GET", path, "Bearer " + TestTokens.valid(WRITER)).chained());
        assertRejected(call("GET", path, "Bearer " + TestTokens.valid(READER)), 403, "FORBIDDEN");
        assertRejected(call("PATCH", path, "Bearer " + TestTokens.valid(READER)), 403, "FORBIDDEN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rooms", "/rooms/", "/rooms/22222222-2222-4222-8222-222222222222"})
    void everyRouteOfTheRoomsNeedsTheRoomWritePermissionAndNoOtherOne(String path) throws Exception {
        assertTrue(call("GET", path, "Bearer " + TestTokens.valid(ROOM_WRITER)).chained());
        assertTrue(call("PATCH", path, "Bearer " + TestTokens.valid(ROOM_WRITER)).chained());
        assertRejected(call("GET", path, "Bearer " + TestTokens.valid(CATALOG_WRITER)), 403, "FORBIDDEN");
        assertRejected(call("PATCH", path, "Bearer " + TestTokens.valid(CATALOG_WRITER)), 403, "FORBIDDEN");
        assertRejected(call("GET", path, "Bearer " + TestTokens.valid(READER)), 403, "FORBIDDEN");
    }

    @Test
    void theRoomPermissionDoesNotOpenTheMoviesAndTheMoviePermissionDoesNotOpenTheRooms() throws Exception {
        assertRejected(post("/movies", TestTokens.valid(ROOM_WRITER)), 403, "FORBIDDEN");
        assertRejected(post("/rooms", TestTokens.valid(CATALOG_WRITER)), 403, "FORBIDDEN");
        assertTrue(post("/rooms", TestTokens.valid(List.of("catalog:write", "room:write"))).chained());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/showtimes", "/showtimes/", "/showtimes/33333333-3333-4333-8333-333333333333"})
    void everyRouteOfTheShowtimesNeedsTheShowtimeWritePermissionAndNoOtherOne(String path) throws Exception {
        assertTrue(call("GET", path, "Bearer " + TestTokens.valid(SHOWTIME_WRITER)).chained());
        assertTrue(call("DELETE", path, "Bearer " + TestTokens.valid(SHOWTIME_WRITER)).chained());
        assertRejected(call("GET", path, "Bearer " + TestTokens.valid(CATALOG_WRITER)), 403, "FORBIDDEN");
        assertRejected(call("GET", path, "Bearer " + TestTokens.valid(ROOM_WRITER)), 403, "FORBIDDEN");
        assertRejected(call("GET", path, "Bearer " + TestTokens.valid(READER)), 403, "FORBIDDEN");
    }

    @Test
    void theShowtimePermissionDoesNotOpenTheMoviesOrTheRooms() throws Exception {
        assertRejected(post("/movies", TestTokens.valid(SHOWTIME_WRITER)), 403, "FORBIDDEN");
        assertRejected(post("/rooms", TestTokens.valid(SHOWTIME_WRITER)), 403, "FORBIDDEN");
        assertRejected(post("/showtimes", TestTokens.valid(CATALOG_WRITER)), 403, "FORBIDDEN");
        assertRejected(post("/showtimes", TestTokens.valid(ROOM_WRITER)), 403, "FORBIDDEN");
    }

    @Test
    void theAvailabilityOfAShowtimeIsNotServedHereSoTheShowtimePermissionDoesNotOpenIt() throws Exception {
        String path = "/showtimes/33333333-3333-4333-8333-333333333333/availability";

        assertRejected(call("GET", path, "Bearer " + TestTokens.valid(SHOWTIME_WRITER)), 403, "FORBIDDEN");
        assertRejected(call("GET", path, "Bearer " + TestTokens.valid(List.of("showtime:write", "catalog:write"))),
                403, "FORBIDDEN");
    }

    @Test
    void aValidTokenWithoutThePermissionIsForbiddenWithTheEnvelope() throws Exception {
        Outcome outcome = post("/movies", TestTokens.valid(READER));

        assertRejected(outcome, 403, "FORBIDDEN");
        assertEquals("You are not authorized to perform this action", body(outcome).get("message").asText());
    }

    @Test
    void aTokenWithoutAPermissionsClaimHasNoPermission() throws Exception {
        assertRejected(post("/movies", TestTokens.rs256(
                TestTokens.claims(TestTokens.SUBJECT, null, TestTokens.inOneHour()).build())), 403, "FORBIDDEN");
    }

    @Test
    void aRouteWithoutARuleIsForbiddenSoANewRouteCannotBeOpenByOmission() throws Exception {
        assertRejected(post("/showtimesx", TestTokens.valid(SHOWTIME_WRITER)), 403, "FORBIDDEN");
        assertRejected(post("/moviesx", TestTokens.valid(WRITER)), 403, "FORBIDDEN");
        assertRejected(post("/roomsx", TestTokens.valid(ROOM_WRITER)), 403, "FORBIDDEN");
        assertRejected(post("/nothing", TestTokens.valid(WRITER)), 403, "FORBIDDEN");
    }

    @Test
    void theRouteIsTheOneTheContainerNormalizedWhateverWayItSplitsItInServletPathAndPathInfo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/catalog/movies");
        request.setContextPath("/api/v1/catalog");
        request.setServletPath("");
        request.setPathInfo("/movies");
        request.addHeader("Authorization", "Bearer " + TestTokens.valid(READER));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertFalse(chain.getRequest() != null);
    }

    @Test
    void theHealthAndBillboardRoutesNeedNoToken() throws Exception {
        assertTrue(call("GET", "/health", null).chained());
        assertTrue(call("GET", "/health/ready", null).chained());
        assertTrue(call("GET", "/billboard", null).chained());
    }

    @Test
    void aRequestWithoutTokenIsUnauthorized() throws Exception {
        Outcome outcome = call("GET", "/movies", null);

        assertRejected(outcome, 401, "UNAUTHORIZED");
        assertEquals("Authentication token required", body(outcome).get("message").asText());
    }

    @Test
    void aSchemeOtherThanBearerIsUnauthorized() throws Exception {
        assertRejected(call("GET", "/movies", "Basic dXNlcjpwYXNz"), 401, "UNAUTHORIZED");
    }

    @Test
    void theBearerSchemeIsCaseInsensitive() throws Exception {
        assertTrue(call("GET", "/movies", "bearer " + TestTokens.valid(WRITER)).chained());
    }

    @Test
    void aMalformedTokenIsInvalid() throws Exception {
        assertRejected(post("/movies", "not-a-jwt"), 401, "INVALID_TOKEN");
    }

    @Test
    void aTokenSignedWithAnotherAlgorithmIsInvalid() throws Exception {
        assertRejected(post("/movies", TestTokens.hs256(WRITER)), 401, "INVALID_TOKEN");
        assertRejected(post("/movies", TestTokens.unsigned(WRITER)), 401, "INVALID_TOKEN");
    }

    @Test
    void aTokenSignedWithAnotherKeyIsInvalid() throws Exception {
        assertRejected(post("/movies", TestTokens.signedByAnotherKey(WRITER)), 401, "INVALID_TOKEN");
    }

    @Test
    void anExpiredTokenOrOneWithoutExpirationIsInvalid() throws Exception {
        assertRejected(post("/movies", TestTokens.rs256(
                TestTokens.claims(TestTokens.SUBJECT, WRITER, TestTokens.anHourAgo()).build())), 401, "INVALID_TOKEN");
        assertRejected(post("/movies", TestTokens.rs256(
                TestTokens.claims(TestTokens.SUBJECT, WRITER, null).build())), 401, "INVALID_TOKEN");
    }

    @Test
    void aTokenWhoseSubjectIsMissingOrNotAUserIdIsInvalid() throws Exception {
        Date exp = TestTokens.inOneHour();
        assertRejected(post("/movies", TestTokens.rs256(TestTokens.claims(null, WRITER, exp).build())),
                401, "INVALID_TOKEN");
        assertRejected(post("/movies", TestTokens.rs256(TestTokens.claims("admin", WRITER, exp).build())),
                401, "INVALID_TOKEN");
    }

    @Test
    void aPermissionsClaimThatIsNotAListOfStringsIsInvalid() throws Exception {
        assertRejected(post("/movies", TestTokens.rs256(TestTokens.claims(TestTokens.SUBJECT, null, TestTokens.inOneHour())
                .claim("permissions", "catalog:write").build())), 401, "INVALID_TOKEN");
    }

    @Test
    void theAnswerNeverEchoesTheToken() throws Exception {
        String token = TestTokens.hs256(WRITER);

        Outcome outcome = post("/movies", token);

        assertFalse(outcome.response().getContentAsString().contains(token));
        assertNull(outcome.request().getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE));
    }
}
