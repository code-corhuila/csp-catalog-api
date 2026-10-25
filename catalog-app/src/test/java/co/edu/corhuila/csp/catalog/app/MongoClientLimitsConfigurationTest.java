package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mongodb.MongoClientSettings;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** The limits of the MongoDB client are declared, and they reach the client settings (Norma 5.3.10). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoClientLimitsConfigurationTest extends ApplicationContextTest {

    @Autowired
    private Environment environment;

    @Test
    void theCustomizerAppliesTheDeclaredLimitsToTheClientSettings() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();

        new MongoClientLimitsConfiguration()
                .mongoLimits(10, Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5),
                        Duration.ofSeconds(5))
                .customize(builder);
        MongoClientSettings settings = builder.build();

        assertEquals(10, settings.getConnectionPoolSettings().getMaxSize());
        assertEquals(5000, settings.getConnectionPoolSettings().getMaxWaitTime(TimeUnit.MILLISECONDS));
        assertEquals(5000, settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS));
        assertEquals(5000, settings.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS));
        assertEquals(5000, settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS));
    }

    @Test
    void everyLimitIsDeclaredInTheConfigurationOfTheService() {
        assertEquals("10", environment.getProperty("catalog.mongodb.max-pool-size"));
        assertEquals("5s", environment.getProperty("catalog.mongodb.connection-wait"));
        assertEquals("5s", environment.getProperty("catalog.mongodb.connect-timeout"));
        assertEquals("5s", environment.getProperty("catalog.mongodb.server-selection-timeout"));
        assertEquals("5s", environment.getProperty("catalog.mongodb.read-timeout"));
    }
}
