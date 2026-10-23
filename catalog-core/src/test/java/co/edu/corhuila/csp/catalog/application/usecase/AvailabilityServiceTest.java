package co.edu.corhuila.csp.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.port.out.MovieWrite;
import co.edu.corhuila.csp.catalog.application.port.out.RoomPage;
import co.edu.corhuila.csp.catalog.application.port.out.RoomRepository;
import co.edu.corhuila.csp.catalog.application.port.out.RoomWrite;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeAvailability;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimePage;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeRepository;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeWrite;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import co.edu.corhuila.csp.catalog.domain.model.TimeInterval;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The availability read of a showtime (ADR-042), with fakes of the output ports (Annex C). */
class AvailabilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MOVIE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ROOM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant START = Instant.parse("2026-11-01T20:00:00Z");
    private static final Instant END = Instant.parse("2026-11-01T22:00:00Z");

    private final FakeShowtimes showtimes = new FakeShowtimes();
    private final FakeMovies movies = new FakeMovies();
    private final FakeRooms rooms = new FakeRooms();
    private final AvailabilityService service = new AvailabilityService(showtimes, movies, rooms);

    private static Showtime showtime(ShowtimeStatus status) {
        return new Showtime(ID, MOVIE_ID, ROOM_ID, "Snapshot Title", "Snapshot Room", new TimeInterval(START, END),
                15000L, "en", "es", status, NOW, NOW);
    }

    private static Movie movie(String title, PublicationStatus status) {
        MovieDetails details = new MovieDetails(title, null, 120, "PG-13", null, null, null, null);
        return Movie.create(MOVIE_ID, details,
                status == PublicationStatus.ARCHIVED ? PublicationStatus.DRAFT : status, NOW).withStatus(status, NOW);
    }

    private static Room room(String name, String... labels) {
        List<Seat> seats = Arrays.stream(labels)
                .map(label -> new Seat(label, null, null, SeatType.STANDARD)).toList();
        return Room.create(ROOM_ID, new RoomDetails(name, seats), NOW);
    }

    private static Room roomOf(String name, Seat... seats) {
        return Room.create(ROOM_ID, new RoomDetails(name, List.of(seats)), NOW);
    }

    private void available() {
        showtimes.stored.put(ID, showtime(ShowtimeStatus.SCHEDULED));
        movies.stored.put(MOVIE_ID, movie("Current Title", PublicationStatus.PUBLISHED));
        rooms.stored.put(ROOM_ID, room("Current Room", "B2", "A1", "A2"));
    }

    private void assertNotFound(UUID showtimeId) {
        ShowtimeNotFoundException error = assertThrows(ShowtimeNotFoundException.class,
                () -> service.getAvailability(showtimeId));
        assertTrue(error.getMessage().contains(showtimeId.toString()));
    }

    @Test
    void aScheduledShowtimeOfAPublishedMovieInAnActiveRoomAnswersEveryField() {
        available();

        ShowtimeAvailability availability = service.getAvailability(ID);

        assertEquals(ID, availability.showtimeId());
        assertEquals(START, availability.startsAt());
        assertEquals(END, availability.endsAt());
        assertEquals(15000L, availability.priceCents());
    }

    @Test
    void theTitleAndTheRoomNameAreTheSnapshotsOfTheShowtimeNotTheCurrentNames() {
        available();

        ShowtimeAvailability availability = service.getAvailability(ID);

        assertEquals("Snapshot Title", availability.movieTitle());
        assertEquals("Snapshot Room", availability.roomName());
    }

    @Test
    void theSeatsAreTheLabelsOfTheRoomInTheOrderTheyAreStored() {
        available();

        assertEquals(List.of("B2", "A1", "A2"), service.getAvailability(ID).seats());
    }

    @Test
    void theSeatLayoutHasTheLabelTheRowTheNumberAndTheTypeOfEverySeatInTheOrderTheyAreStored() {
        available();
        rooms.stored.put(ROOM_ID, roomOf("Current Room",
                new Seat("B1", "B", 1, SeatType.VIP),
                new Seat("A1", "A", 1, SeatType.STANDARD),
                new Seat("A2", "A", 2, SeatType.ACCESSIBLE)));

        assertEquals(List.of(
                new Seat("B1", "B", 1, SeatType.VIP),
                new Seat("A1", "A", 1, SeatType.STANDARD),
                new Seat("A2", "A", 2, SeatType.ACCESSIBLE)), service.getAvailability(ID).seatLayout());
    }

    @Test
    void aSeatWithNoRowOrNumberKeepsThemAbsentAndKeepsItsType() {
        available();
        rooms.stored.put(ROOM_ID, roomOf("Current Room", new Seat("A1", null, null, SeatType.VIP), new Seat("A2", null, null, null)));

        List<Seat> layout = service.getAvailability(ID).seatLayout();

        assertEquals(null, layout.get(0).row());
        assertEquals(null, layout.get(0).number());
        assertEquals(SeatType.VIP, layout.get(0).type());
        assertEquals(SeatType.STANDARD, layout.get(1).type());
    }

    @Test
    void theSeatsAreAlwaysTheLabelsOfTheSeatLayout() {
        available();

        ShowtimeAvailability availability = service.getAvailability(ID);

        assertEquals(availability.seatLayout().stream().map(Seat::label).toList(), availability.seats());
    }

    @Test
    void aLayoutChangeOfTheRoomShowsInTheNextCall() {
        available();
        service.getAvailability(ID);
        rooms.stored.put(ROOM_ID, room("Current Room", "A1", "A2", "A3", "A4"));

        assertEquals(List.of("A1", "A2", "A3", "A4"), service.getAvailability(ID).seats());
    }

    @Test
    void anUnknownShowtimeIsNotFound() {
        available();

        assertNotFound(UUID.fromString("99999999-9999-4999-8999-999999999999"));
    }

    @Test
    void aCancelledShowtimeIsNotFound() {
        available();
        showtimes.stored.put(ID, showtime(ShowtimeStatus.CANCELLED));

        assertNotFound(ID);
    }

    @Test
    void aCompletedShowtimeIsNotFound() {
        available();
        showtimes.stored.put(ID, showtime(ShowtimeStatus.COMPLETED));

        assertNotFound(ID);
    }

    @Test
    void aShowtimeOfADraftMovieIsNotFound() {
        available();
        movies.stored.put(MOVIE_ID, movie("Current Title", PublicationStatus.DRAFT));

        assertNotFound(ID);
    }

    @Test
    void aShowtimeOfAnArchivedMovieIsNotFound() {
        available();
        movies.stored.put(MOVIE_ID, movie("Current Title", PublicationStatus.ARCHIVED));

        assertNotFound(ID);
    }

    @Test
    void aShowtimeOfAnArchivedRoomIsNotFound() {
        available();
        rooms.stored.put(ROOM_ID, room("Current Room", "A1").archive(NOW));

        assertNotFound(ID);
    }

    @Test
    void aShowtimeWhoseMovieDocumentIsMissingIsNotFound() {
        available();
        movies.stored.clear();

        assertNotFound(ID);
    }

    @Test
    void aShowtimeWhoseRoomDocumentIsMissingIsNotFound() {
        available();
        rooms.stored.clear();

        assertNotFound(ID);
    }

    @Test
    void theReadNeverWrites() {
        available();

        service.getAvailability(ID);

        assertEquals(0, showtimes.saves + movies.saves + rooms.saves);
    }

    /** In-memory showtimes: only the lookup by id is used, and a save is counted. */
    private static final class FakeShowtimes implements ShowtimeRepository {

        final Map<UUID, Showtime> stored = new HashMap<>();
        int saves;

        @Override
        public Optional<UUID> findIdempotentResource(String key, String requestHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Showtime> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public ShowtimePage findAll(int page, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Showtime save(ShowtimeWrite write) {
            saves++;
            return write.showtime();
        }
    }

    /** In-memory movies: only the lookup by id is used, and a save is counted. */
    private static final class FakeMovies implements MovieRepository {

        final Map<UUID, Movie> stored = new HashMap<>();
        int saves;

        @Override
        public Optional<UUID> findIdempotentResource(String key, String requestHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Movie> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public MoviePage findActive(int page, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Movie save(MovieWrite write) {
            saves++;
            return write.movie();
        }
    }

    /** In-memory rooms: only the lookup by id is used, and a save is counted. */
    private static final class FakeRooms implements RoomRepository {

        final Map<UUID, Room> stored = new HashMap<>();
        int saves;

        @Override
        public Optional<UUID> findIdempotentResource(String key, String requestHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Room> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public RoomPage findActive(int page, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Room save(RoomWrite write) {
            saves++;
            return write.room();
        }
    }
}
