package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The composition root really wires the HTTP adapter: the service answers under the base path of the
 * contract, the token is checked before any route, and every rejection is the error envelope that carries
 * the correlation id of the request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpEnvelopeWiringTest extends ApplicationContextTest {

    private static final String CORRELATION = "550e8400-e29b-41d4-a716-446655440000";
    private static final String MOVIE = "/movies/11111111-1111-4111-8111-111111111111";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Correlation-Id", CORRELATION);
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void assertEnvelope(ResponseEntity<String> answer, HttpStatus status, String error) throws Exception {
        assertEquals(status, answer.getStatusCode());
        assertEquals(CORRELATION, answer.getHeaders().getFirst("X-Correlation-Id"));
        JsonNode body = json.readTree(answer.getBody());
        assertEquals(error, body.get("error").asText());
        assertEquals(CORRELATION, body.get("traceId").asText());
        assertTrue(body.has("message"));
    }

    @Test
    void aRequestWithoutTokenIsRejectedWithTheEnvelopeBeforeAnyRoute() throws Exception {
        assertEnvelope(get(MOVIE, null), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void aTokenWithoutTheWritePermissionIsForbidden() throws Exception {
        assertEnvelope(get(MOVIE, RsaTestKey.token(List.of("catalog:read"))), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void aTokenWithTheWritePermissionPassesTheSecurityAndMeetsTheRoutesOfTheService() throws Exception {
        assertEnvelope(get(MOVIE, RsaTestKey.token(List.of("catalog:write"))), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void theServiceAnswersUnderTheBasePathOfTheContract() {
        assertTrue(http.getRestTemplate().getUriTemplateHandler().expand("/x").getPath()
                .startsWith("/api/v1/catalog"));
    }
}
