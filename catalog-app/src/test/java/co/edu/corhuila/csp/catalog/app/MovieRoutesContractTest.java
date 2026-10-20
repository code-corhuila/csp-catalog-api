package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Contract tests of the movie routes: every request goes through the real stack (security filter,
 * controller, use cases, MongoDB with the schema of csp-catalog-db) and both the request and the answer are
 * validated against the official {@code catalog-service.yaml} of csp-docs. They run when
 * TEST_CONTRACT_FILE points to that file and TEST_MONGODB_URI to a disposable database, and they fail when CI
 * is set and either is missing, so a green CI ran them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MovieRoutesContractTest extends ApplicationContextTest {

    private static final String BASE = "/api/v1/catalog";
    private static final String CREATE = """
            {"title":"Synthetic Movie","synopsis":"A synopsis","durationMinutes":120,"classification":"PG-13",
             "releaseDate":"2026-12-01","genres":["Drama"],"cast":["Synthetic Actor"],
             "posterUrl":"https://example.com/poster.png","status":"DRAFT"}""";

    private static OpenApiInteractionValidator contract;
    /** The same contract without the verdict on the request: the invalid requests of the error cases break it on purpose. */
    private static OpenApiInteractionValidator contractOfTheAnswer;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private ObjectMapper json;

    @BeforeAll
    static void readTheContract() {
        String file = System.getenv("TEST_CONTRACT_FILE");
        String uri = System.getenv("TEST_MONGODB_URI");
        boolean configured = file != null && !file.isBlank() && uri != null && !uri.isBlank();
        if (!configured && "true".equals(System.getenv("CI"))) {
            fail("TEST_CONTRACT_FILE and TEST_MONGODB_URI are required in CI: a green build must mean the contract tests ran");
        }
        assumeTrue(configured, "TEST_CONTRACT_FILE or TEST_MONGODB_URI is not set: the contract tests were skipped");
        String spec = Path.of(file).toUri().toString();
        contract = OpenApiInteractionValidator.createFor(spec).withResolveCombinators(true).build();
        contractOfTheAnswer = OpenApiInteractionValidator.createFor(spec).withResolveCombinators(true)
                .withLevelResolver(LevelResolver.create().withLevel("validation.request", ValidationReport.Level.IGNORE).build())
                .build();
    }

    @DynamicPropertySource
    static void mongoUri(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("TEST_MONGODB_URI"));
    }

    @BeforeEach
    void emptyTheCollections() {
        mongo.getCollection("movies").deleteMany(new org.bson.Document());
        mongo.getCollection("idempotency_key").deleteMany(new org.bson.Document());
    }

    private static String key() {
        return "contract-" + UUID.randomUUID();
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, List<String> permissions) {
        return request.contextPath(BASE).header("Authorization", "Bearer " + RsaTestKey.token(permissions));
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return authorized(request, List.of("catalog:read", "catalog:write"));
    }

    /** Performs the request and checks that both it and its answer match the contract. */
    private MvcResult call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return check(request, expectedStatus, contract);
    }

    /** Performs a request that breaks the contract on purpose and checks that its answer matches it. */
    private MvcResult callWithInvalidRequest(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return check(request, expectedStatus, contractOfTheAnswer);
    }

    private MvcResult check(MockHttpServletRequestBuilder request, int expectedStatus, OpenApiInteractionValidator validator)
            throws Exception {
        return mvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(OpenApiValidationMatchers.openApi().isValid(validator))
                .andReturn();
    }

    private MockHttpServletRequestBuilder create(String key, String body) {
        return admin(post(BASE + "/movies")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
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
