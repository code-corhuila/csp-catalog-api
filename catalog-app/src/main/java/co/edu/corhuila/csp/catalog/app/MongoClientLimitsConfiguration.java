package co.edu.corhuila.csp.catalog.app;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The explicit limits of the MongoDB client (Norma 5.3.10): the size of the pool, the wait for a
 * connection, the time to select a server and the time of a read. The values are declared in
 * {@code application.yml}, so a connection URI from the environment cannot remove them.
 */
@Configuration
class MongoClientLimitsConfiguration {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoLimits(
            @Value("${catalog.mongodb.max-pool-size}") int maxPoolSize,
            @Value("${catalog.mongodb.connection-wait}") Duration connectionWait,
            @Value("${catalog.mongodb.connect-timeout}") Duration connectTimeout,
            @Value("${catalog.mongodb.server-selection-timeout}") Duration serverSelectionTimeout,
            @Value("${catalog.mongodb.read-timeout}") Duration readTimeout) {
        return builder -> builder
                .applyToConnectionPoolSettings(pool -> pool
                        .maxSize(maxPoolSize)
                        .maxWaitTime(connectionWait.toMillis(), TimeUnit.MILLISECONDS))
                .applyToSocketSettings(socket -> socket
                        .connectTimeout((int) connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout((int) readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                .applyToClusterSettings(cluster -> cluster
                        .serverSelectionTimeout(serverSelectionTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }
}
