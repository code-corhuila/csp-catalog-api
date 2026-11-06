package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * The database probe of the readiness route (DEC-012). The reachable case runs against a real MongoDB when
 * TEST_MONGODB_URI is set (as {@code catalog_app} in CI, which proves the ping needs no privilege) and fails
 * when CI is set without it; the unreachable case needs no database and always runs.
 */
class MongoDatabaseProbeTest {

    @Test
    void answersWhenMongoDbAnswersThePing() {
        String uri = System.getenv("TEST_MONGODB_URI");
        boolean configured = uri != null && !uri.isBlank();
        if (!configured && "true".equals(System.getenv("CI"))) {
            fail("TEST_MONGODB_URI is required in CI: a green build must mean the MongoDB tests ran");
        }
        assumeTrue(configured, "TEST_MONGODB_URI is not set: the MongoDB integration tests were skipped");

        assertTrue(new MongoDatabaseProbe(new MongoTemplate(new SimpleMongoClientDatabaseFactory(uri))).answers());
    }

    @Test
    void doesNotAnswerWhenNoMongoDbListens() {
        String unreachable = "mongodb://localhost:1/catalog_db?serverSelectionTimeoutMS=300&connectTimeoutMS=300";

        assertFalse(new MongoDatabaseProbe(new MongoTemplate(new SimpleMongoClientDatabaseFactory(unreachable))).answers());
    }
}
