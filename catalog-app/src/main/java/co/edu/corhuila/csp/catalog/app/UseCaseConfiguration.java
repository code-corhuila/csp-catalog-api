package co.edu.corhuila.csp.catalog.app;

import co.edu.corhuila.csp.catalog.adapter.out.persistence.MongoBillboardReader;
import co.edu.corhuila.csp.catalog.adapter.out.persistence.MongoDatabaseProbe;
import co.edu.corhuila.csp.catalog.adapter.out.persistence.MongoMovieRepository;
import co.edu.corhuila.csp.catalog.adapter.out.persistence.MongoMovieSearchReader;
import co.edu.corhuila.csp.catalog.adapter.out.persistence.MongoRoomRepository;
import co.edu.corhuila.csp.catalog.adapter.out.persistence.MongoShowtimeRepository;
import co.edu.corhuila.csp.catalog.application.port.in.AvailabilityUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.BillboardUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.MovieUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.ReadinessUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.RoomUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.ShowtimeUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardReader;
import co.edu.corhuila.csp.catalog.application.port.out.DatabaseProbe;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchReader;
import co.edu.corhuila.csp.catalog.application.port.out.RoomRepository;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeRepository;
import co.edu.corhuila.csp.catalog.application.usecase.AvailabilityService;
import co.edu.corhuila.csp.catalog.application.usecase.BillboardService;
import co.edu.corhuila.csp.catalog.application.usecase.MovieSearchService;
import co.edu.corhuila.csp.catalog.application.usecase.MovieService;
import co.edu.corhuila.csp.catalog.application.usecase.ReadinessService;
import co.edu.corhuila.csp.catalog.application.usecase.RoomService;
import co.edu.corhuila.csp.catalog.application.usecase.ShowtimeService;
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

    @Bean
    RoomRepository roomRepository(MongoTemplate mongo) {
        return new MongoRoomRepository(mongo);
    }

    @Bean
    RoomUseCases roomUseCases(RoomRepository rooms) {
        return new RoomService(rooms, UUID::randomUUID, Clock.systemUTC());
    }

    @Bean
    ShowtimeRepository showtimeRepository(MongoTemplate mongo) {
        return new MongoShowtimeRepository(mongo);
    }

    @Bean
    ShowtimeUseCases showtimeUseCases(ShowtimeRepository showtimes, MovieRepository movies, RoomRepository rooms) {
        return new ShowtimeService(showtimes, movies, rooms, UUID::randomUUID, Clock.systemUTC());
    }

    @Bean
    AvailabilityUseCases availabilityUseCases(ShowtimeRepository showtimes, MovieRepository movies, RoomRepository rooms) {
        return new AvailabilityService(showtimes, movies, rooms);
    }

    @Bean
    BillboardReader billboardReader(MongoTemplate mongo) {
        return new MongoBillboardReader(mongo);
    }

    @Bean
    BillboardUseCases billboardUseCases(BillboardReader reader) {
        return new BillboardService(reader, Clock.systemUTC());
    }

    @Bean
    DatabaseProbe databaseProbe(MongoTemplate mongo) {
        return new MongoDatabaseProbe(mongo);
    }

    @Bean
    ReadinessUseCases readinessUseCases(DatabaseProbe database) {
        return new ReadinessService(database);
    }

    @Bean
    MovieSearchReader movieSearchReader(MongoTemplate mongo) {
        return new MongoMovieSearchReader(mongo);
    }

    @Bean
    MovieSearchUseCases movieSearchUseCases(MovieSearchReader reader) {
        return new MovieSearchService(reader);
    }
}
