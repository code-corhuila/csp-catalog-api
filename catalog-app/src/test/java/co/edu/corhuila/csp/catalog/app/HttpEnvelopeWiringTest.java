package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * contract, and a route that does not exist answers the error envelope with the correlation id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpEnvelopeWiringTest {

    private static final String CORRELATION = "550e8400-e29b-41d4-a716-446655440000";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Test
    void anUnknownRouteAnswersTheEnvelopeAndTheCorrelationIdOfTheRequest() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Correlation-Id", CORRELATION);

        ResponseEntity<String> answer = http.exchange("/nothing-here", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.NOT_FOUND, answer.getStatusCode());
        assertEquals(CORRELATION, answer.getHeaders().getFirst("X-Correlation-Id"));
        JsonNode body = json.readTree(answer.getBody());
        assertEquals("NOT_FOUND", body.get("error").asText());
        assertEquals(CORRELATION, body.get("traceId").asText());
        assertTrue(body.has("message"));
    }

    @Test
    void theServiceAnswersUnderTheBasePathOfTheContract() {
        assertNotNull(http.getRootUri());
        assertTrue(http.getRestTemplate().getUriTemplateHandler().expand("/x").getPath()
                .startsWith("/api/v1/catalog"));
    }
}
