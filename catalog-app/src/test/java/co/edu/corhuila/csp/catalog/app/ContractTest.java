package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base of the contract tests: every request goes through the real stack (security filter, controller, use
 * cases, MongoDB with the schema of csp-catalog-db) and both the request and the answer are validated against
 * the official {@code catalog-service.yaml} of csp-docs. They run when TEST_CONTRACT_FILE points to that file
 * and TEST_MONGODB_URI to a disposable database, and they fail when CI is set and either is missing, so a
 * green CI ran them.
 */
abstract class ContractTest extends ApplicationContextTest {

    protected static final String BASE = "/api/v1/catalog";

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
        mongo.getCollection("rooms").deleteMany(new org.bson.Document());
        mongo.getCollection("showtimes").deleteMany(new org.bson.Document());
        mongo.getCollection("outbox_event").deleteMany(new org.bson.Document());
        mongo.getCollection("idempotency_key").deleteMany(new org.bson.Document());
    }

    protected static String key() {
        return "contract-" + UUID.randomUUID();
    }

    protected static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, List<String> permissions) {
        return request.contextPath(BASE).header("Authorization", "Bearer " + RsaTestKey.token(permissions));
    }

    /** Performs the request and checks that both it and its answer match the contract. */
    protected MvcResult call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return check(request, expectedStatus, contract);
    }

    /** Performs a request that breaks the contract on purpose and checks that its answer matches it. */
    protected MvcResult callWithInvalidRequest(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return check(request, expectedStatus, contractOfTheAnswer);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult check(MockHttpServletRequestBuilder request, int expectedStatus, OpenApiInteractionValidator validator)
            throws Exception {
        return mvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(OpenApiValidationMatchers.openApi().isValid(validator))
                .andReturn();
    }
}
