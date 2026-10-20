package co.edu.corhuila.csp.catalog.app;

import co.edu.corhuila.csp.catalog.adapter.out.persistence.MongoMovieRepository;
import co.edu.corhuila.csp.catalog.application.port.in.MovieUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.usecase.MovieService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Wires the use cases to their adapters. This is the only place that knows the concrete output adapter, the
 * source of identifiers and the clock; the domain and the use cases receive them through their ports.
 */
@Configuration
class UseCaseConfiguration {

    @Bean
    MovieRepository movieRepository(MongoTemplate mongo) {
        return new MongoMovieRepository(mongo);
    }

    @Bean
    MovieUseCases movieUseCases(MovieRepository movies) {
        return new MovieService(movies, UUID::randomUUID, Clock.systemUTC());
    }
}
