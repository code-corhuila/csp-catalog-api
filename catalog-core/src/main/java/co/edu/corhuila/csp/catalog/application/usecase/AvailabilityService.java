package co.edu.corhuila.csp.catalog.application.usecase;

import co.edu.corhuila.csp.catalog.application.port.in.AvailabilityUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.port.out.RoomRepository;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeAvailability;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeRepository;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException;
import java.util.UUID;

/**
 * The availability of a showtime for Booking (ADR-042). It only reads: the showtime must be scheduled, its movie
 * published and its room active, and every other case, a missing document included, is not found. The time of
 * the showtime is not judged: Booking decides what a started showtime means.
 */
public class AvailabilityService implements AvailabilityUseCases {

    private final ShowtimeRepository showtimes;
    private final MovieRepository movies;
    private final RoomRepository rooms;

    public AvailabilityService(ShowtimeRepository showtimes, MovieRepository movies, RoomRepository rooms) {
        this.showtimes = showtimes;
        this.movies = movies;
        this.rooms = rooms;
    }

    @Override
    public ShowtimeAvailability getAvailability(UUID showtimeId) {
        Showtime showtime = showtimes.findById(showtimeId)
                .filter(Showtime::isScheduled)
                .orElseThrow(() -> new ShowtimeNotFoundException(showtimeId));
        movies.findById(showtime.movieId())
                .filter(AvailabilityService::isPublished)
                .orElseThrow(() -> new ShowtimeNotFoundException(showtimeId));
        Room room = rooms.findById(showtime.roomId())
                .filter(candidate -> !candidate.isArchived())
                .orElseThrow(() -> new ShowtimeNotFoundException(showtimeId));
        return new ShowtimeAvailability(showtime.id(), showtime.movieTitleSnapshot(), showtime.roomNameSnapshot(),
                showtime.interval().start(), showtime.interval().end(), showtime.priceCents(),
                room.details().seats().stream().map(Seat::label).toList());
    }

    private static boolean isPublished(Movie movie) {
        return movie.status() == PublicationStatus.PUBLISHED && !movie.isArchived();
    }
}
