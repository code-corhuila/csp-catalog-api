package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import java.util.List;
import java.util.Set;
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
    private static final String SERVICE = "booking-service";
    private static final String AVAILABILITY = "/showtimes/33333333-3333-4333-8333-333333333333/availability";
    private static final List<String> ADMINISTRATOR = List.of("showtime:write", "catalog:write", "room:write");

    private final JwtAuthenticationFilter filter = filterFor(SERVICE);

    private JwtAuthenticationFilter filterFor(String... serviceSubjects) {
        return new JwtAuthenticationFilter(TestTokens.publicKey(), json, new ServiceSubjects(Set.of(serviceSubjects)));
    }

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
        return call(filter, method, path, authorization);
    }

    private Outcome call(JwtAuthenticationFilter using, String method, String path, String authorization)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/catalog" + path);
        request.setContextPath("/api/v1/catalog");
        request.setServletPath(path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        using.doFilter(request, response, chain);
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
    void theBillboardIgnoresATokenThatIsSentWhateverItIs() throws Exception {
        assertTrue(call("GET", "/billboard", "Bearer not-a-jwt").chained());
        assertTrue(call("GET", "/billboard", "Bearer " + TestTokens.hs256(WRITER)).chained());
        assertTrue(call("GET", "/billboard", "Bearer " + TestTokens.signedByAnotherKey(WRITER)).chained());
        assertTrue(call("GET", "/billboard", "Bearer " + TestTokens.rs256(
                TestTokens.claims(TestTokens.SUBJECT, WRITER, TestTokens.anHourAgo()).build())).chained());
        assertTrue(call("GET", "/billboard", "Basic dXNlcjpwYXNz").chained());
        assertTrue(call("GET", "/billboard", "Bearer " + TestTokens.valid(READER)).chained());
        assertTrue(call("GET", "/billboard", "Bearer " + TestTokens.valid(List.of())).chained());
    }

    @Test
    void theMovieSearchNeedsNoToken() throws Exception {
        assertTrue(call("GET", "/billboard/movies", null).chained());
    }

    @Test
    void theMovieSearchIgnoresATokenThatIsSentWhateverItIs() throws Exception {
        assertTrue(call("GET", "/billboard/movies", "Bearer not-a-jwt").chained());
        assertTrue(call("GET", "/billboard/movies", "Bearer " + TestTokens.hs256(WRITER)).chained());
        assertTrue(call("GET", "/billboard/movies", "Bearer " + TestTokens.signedByAnotherKey(WRITER)).chained());
        assertTrue(call("GET", "/billboard/movies", "Bearer " + TestTokens.rs256(
                TestTokens.claims(TestTokens.SUBJECT, WRITER, TestTokens.anHourAgo()).build())).chained());
        assertTrue(call("GET", "/billboard/movies", "Basic dXNlcjpwYXNz").chained());
        assertTrue(call("GET", "/billboard/movies", "Bearer " + TestTokens.valid(READER)).chained());
        assertTrue(call("GET", "/billboard/movies", "Bearer " + TestTokens.valid(List.of())).chained());
    }

    @Test
    void onlyTheReadOfTheExactMovieSearchRouteIsPublic() throws Exception {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            assertRejected(call(method, "/billboard/movies", null), 401, "UNAUTHORIZED");
            assertRejected(call(method, "/billboard/movies", "Bearer " + TestTokens.valid(WRITER)), 403, "FORBIDDEN");
        }
        assertRejected(call("GET", "/billboard/moviesx", null), 401, "UNAUTHORIZED");
        assertRejected(call("GET", "/billboard/movies/", null), 401, "UNAUTHORIZED");
        assertRejected(call("GET", "/billboard/movies/extra", null), 401, "UNAUTHORIZED");
        assertRejected(call("GET", "/billboard/movies/11111111-1111-4111-8111-111111111111", null), 401, "UNAUTHORIZED");
    }

    @Test
    void theMoviesRoutesKeepTheirPermissionWhenTheSearchIsPublic() throws Exception {
        assertRejected(call("GET", "/movies", null), 401, "UNAUTHORIZED");
        assertRejected(call("GET", "/movies", "Bearer " + TestTokens.valid(READER)), 403, "FORBIDDEN");
        assertTrue(call("GET", "/movies", "Bearer " + TestTokens.valid(WRITER)).chained());
    }

    @Test
    void onlyTheReadOfTheExactBillboardRouteIsPublic() throws Exception {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            assertRejected(call(method, "/billboard", null), 401, "UNAUTHORIZED");
            assertRejected(call(method, "/billboard", "Bearer " + TestTokens.valid(WRITER)), 403, "FORBIDDEN");
        }
        assertRejected(call("GET", "/billboardx", null), 401, "UNAUTHORIZED");
        assertRejected(call("POST", "/billboardx", null), 401, "UNAUTHORIZED");
        assertRejected(call("GET", "/billboard/extra", null), 401, "UNAUTHORIZED");
        assertRejected(post("/billboardx", TestTokens.valid(WRITER)), 403, "FORBIDDEN");
    }

    @Test
    void theOtherRoutesStayClosedToAnAnonymousCaller() throws Exception {
        assertRejected(call("GET", "/movies", null), 401, "UNAUTHORIZED");
        assertRejected(call("GET", "/rooms", null), 401, "UNAUTHORIZED");
        assertRejected(call("GET", "/showtimes", null), 401, "UNAUTHORIZED");
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

    private static Date secondsAgo(int seconds) {
        return new Date(System.currentTimeMillis() - seconds * 1000L);
    }

    @Test
    void aTokenThatExpiredAFewSecondsAgoIsStillAcceptedBecauseOfTheClockSkew() throws Exception {
        Outcome outcome = post("/movies", TestTokens.rs256(
                TestTokens.claims(TestTokens.SUBJECT, WRITER, secondsAgo(10)).build()));

        assertTrue(outcome.chained());
    }

    @Test
    void aTokenThatExpiredJustInsideTheThirtySecondsOfSkewIsAccepted() throws Exception {
        Outcome outcome = post("/movies", TestTokens.rs256(
                TestTokens.claims(TestTokens.SUBJECT, WRITER, secondsAgo(25)).build()));

        assertTrue(outcome.chained());
    }

    @Test
    void aTokenThatExpiredBeyondTheThirtySecondsOfSkewIsInvalid() throws Exception {
        assertRejected(post("/movies", TestTokens.rs256(
                TestTokens.claims(TestTokens.SUBJECT, WRITER, secondsAgo(40)).build())), 401, "INVALID_TOKEN");
        assertRejected(post("/movies", TestTokens.rs256(
                TestTokens.claims(TestTokens.SUBJECT, WRITER, secondsAgo(120)).build())), 401, "INVALID_TOKEN");
    }

    @Test
    void theSkewAlsoAppliesToTheTokenOfAServiceOnTheAvailabilityRoute() throws Exception {
        Outcome within = call("GET", AVAILABILITY, "Bearer " + TestTokens.rs256(
                TestTokens.claims(SERVICE, List.of(), secondsAgo(10)).build()));
        Outcome beyond = call("GET", AVAILABILITY, "Bearer " + TestTokens.rs256(
                TestTokens.claims(SERVICE, List.of(), secondsAgo(40)).build()));

        assertTrue(within.chained());
        assertRejected(beyond, 401, "INVALID_TOKEN");
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

    private static String tokenOf(String subject, List<String> permissions) {
        return TestTokens.rs256(TestTokens.claims(subject, permissions, TestTokens.inOneHour()).build());
    }

    private Outcome availability(String token) throws Exception {
        return call("GET", AVAILABILITY, "Bearer " + token);
    }

    @Test
    void aValidTokenOfAServiceSubjectOpensTheAvailabilityWhateverItsPermissions() throws Exception {
        assertTrue(availability(tokenOf(SERVICE, List.of())).chained());
        assertTrue(availability(tokenOf(SERVICE, SHOWTIME_WRITER)).chained());
        assertTrue(availability(tokenOf(SERVICE, ADMINISTRATOR)).chained());
    }

    @Test
    void aServiceCallerIsNotAUserSoNoUserIdIsSet() throws Exception {
        assertNull(availability(tokenOf(SERVICE, List.of())).request().getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE));
    }

    @Test
    void theAvailabilityWithoutTokenIsUnauthorizedLikeEveryOtherRoute() throws Exception {
        assertRejected(call("GET", AVAILABILITY, null), 401, "UNAUTHORIZED");
        assertRejected(call("GET", AVAILABILITY, "Basic dXNlcjpwYXNz"), 401, "UNAUTHORIZED");
    }

    @Test
    void aTokenThatIsNotValidIsInvalidOnTheAvailabilityEvenIfItNamesTheService() throws Exception {
        Date exp = TestTokens.inOneHour();
        assertRejected(availability("not-a-jwt"), 401, "INVALID_TOKEN");
        assertRejected(availability(TestTokens.hs256(List.of())), 401, "INVALID_TOKEN");
        assertRejected(availability(TestTokens.unsigned(List.of())), 401, "INVALID_TOKEN");
        assertRejected(availability(tokenExpiring(SERVICE, TestTokens.anHourAgo())), 401, "INVALID_TOKEN");
        assertRejected(availability(tokenExpiring(SERVICE, null)), 401, "INVALID_TOKEN");
        assertRejected(availability(tokenExpiring(null, exp)), 401, "INVALID_TOKEN");
        assertRejected(availability(tokenExpiring("  ", exp)), 401, "INVALID_TOKEN");
    }

    private static String tokenExpiring(String subject, Date expiresAt) {
        return TestTokens.rs256(TestTokens.claims(subject, List.of(), expiresAt).build());
    }

    @Test
    void aTokenOfAServiceSubjectSignedWithAnotherKeyIsInvalid() throws Exception {
        String forged = TestTokens.signedByAnotherKey(TestTokens.claims(SERVICE, List.of(), TestTokens.inOneHour()).build());

        assertRejected(availability(forged), 401, "INVALID_TOKEN");
    }

    @Test
    void aValidTokenWhoseSubjectIsNotAServiceSubjectIsForbiddenWhateverItsPermissions() throws Exception {
        assertRejected(availability(TestTokens.valid(List.of())), 403, "FORBIDDEN");
        assertRejected(availability(TestTokens.valid(SHOWTIME_WRITER)), 403, "FORBIDDEN");
        assertRejected(availability(TestTokens.valid(ADMINISTRATOR)), 403, "FORBIDDEN");
        assertRejected(availability(tokenOf("payment-service", ADMINISTRATOR)), 403, "FORBIDDEN");
        assertRejected(availability(tokenOf("Booking-Service", List.of())), 403, "FORBIDDEN");
        assertRejected(availability(tokenOf("booking-service ", List.of())), 403, "FORBIDDEN");
    }

    @Test
    void theForbiddenAnswerHasTheEnvelopeAndNeverEchoesTheToken() throws Exception {
        String token = TestTokens.valid(ADMINISTRATOR);

        Outcome outcome = availability(token);

        assertRejected(outcome, 403, "FORBIDDEN");
        assertEquals("You are not authorized to perform this action", body(outcome).get("message").asText());
        assertFalse(outcome.response().getContentAsString().contains(token));
    }

    @Test
    void theAllowListIsTheConfiguredOneAndNoOther() throws Exception {
        JwtAuthenticationFilter two = filterFor("booking-service", "csp-worker");

        assertTrue(call(two, "GET", AVAILABILITY, "Bearer " + tokenOf("booking-service", List.of())).chained());
        assertTrue(call(two, "GET", AVAILABILITY, "Bearer " + tokenOf("csp-worker", List.of())).chained());
        assertRejected(call(two, "GET", AVAILABILITY, "Bearer " + tokenOf("auth-service", List.of())), 403, "FORBIDDEN");
        assertRejected(call(filterFor("csp-worker"), "GET", AVAILABILITY, "Bearer " + tokenOf(SERVICE, List.of())),
                403, "FORBIDDEN");
    }

    @Test
    void anEmptyAllowListOpensTheRouteToNobody() throws Exception {
        JwtAuthenticationFilter nobody = filterFor();

        assertRejected(call(nobody, "GET", AVAILABILITY, "Bearer " + tokenOf(SERVICE, List.of())), 403, "FORBIDDEN");
        assertRejected(call(nobody, "GET", AVAILABILITY, "Bearer " + TestTokens.valid(ADMINISTRATOR)), 403, "FORBIDDEN");
        assertRejected(call(nobody, "GET", AVAILABILITY, null), 401, "UNAUTHORIZED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/movies", "/movies/11111111-1111-4111-8111-111111111111", "/rooms",
            "/rooms/22222222-2222-4222-8222-222222222222", "/showtimes",
            "/showtimes/33333333-3333-4333-8333-333333333333", "/nothing"})
    void aServiceTokenOpensNoOtherRouteWhateverItsPermissions(String path) throws Exception {
        for (String method : List.of("GET", "POST", "PATCH", "DELETE")) {
            assertRejected(call(method, path, "Bearer " + tokenOf(SERVICE, List.of())), 403, "FORBIDDEN");
            assertRejected(call(method, path, "Bearer " + tokenOf(SERVICE, ADMINISTRATOR)), 403, "FORBIDDEN");
        }
    }

    @Test
    void theServiceCredentialIsOnlyForTheReadOfTheExactAvailabilityRoute() throws Exception {
        String service = "Bearer " + tokenOf(SERVICE, List.of());
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE", "HEAD")) {
            assertRejected(call(method, AVAILABILITY, service), 403, "FORBIDDEN");
        }
        assertRejected(call("GET", AVAILABILITY + "x", service), 403, "FORBIDDEN");
        assertRejected(call("GET", AVAILABILITY + "/extra", service), 403, "FORBIDDEN");
        assertRejected(call("GET", "/showtimes/availability", service), 403, "FORBIDDEN");
    }

    @Test
    void aServiceTokenKeepsTheBillboardAndTheHealthChecksOpenAndNothingMore() throws Exception {
        String service = "Bearer " + tokenOf(SERVICE, List.of());

        assertTrue(call("GET", "/billboard", service).chained());
        assertTrue(call("GET", "/billboard/movies", service).chained());
        assertTrue(call("GET", "/health", service).chained());
        assertRejected(call("POST", "/billboard", service), 403, "FORBIDDEN");
        assertRejected(call("POST", "/billboard/movies", service), 403, "FORBIDDEN");
    }
}
