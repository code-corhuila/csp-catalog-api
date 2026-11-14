package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Contract tests of the liveness and readiness routes (DEC-012): no token is sent, the service runs against a real
 * MongoDB as {@code catalog_app}, and both answers are validated against the official {@code catalog-service.yaml}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthRoutesContractTest extends ContractTest {

    @Test
    void livenessAnswersOkWithoutAToken() throws Exception {
        JsonNode answer = body(call(get(BASE + "/health").contextPath(BASE), 200));

        assertEquals("ok", answer.get("status").asText());
        assertFalse(answer.has("database"));
    }

    @Test
    void readinessReportsMongoDbAsConnectedWithoutAToken() throws Exception {
        JsonNode answer = body(call(get(BASE + "/health/ready").contextPath(BASE), 200));

        assertEquals("ready", answer.get("status").asText());
        assertEquals("connected", answer.get("dependencies").get("mongodb").asText());
    }
}
