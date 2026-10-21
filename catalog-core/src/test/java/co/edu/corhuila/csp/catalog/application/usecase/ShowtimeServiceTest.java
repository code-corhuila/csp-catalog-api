package co.edu.corhuila.csp.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.csp.catalog.application.port.in.CancelShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.port.out.MovieWrite;
import co.edu.corhuila.csp.catalog.application.port.out.RoomPage;
import co.edu.corhuila.csp.catalog.application.port.out.RoomRepository;
import co.edu.corhuila.csp.catalog.application.port.out.RoomWrite;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimePage;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeRepository;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.InvalidShowtimeException;
import co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeChanges;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeReferenceException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The use cases of the showtime writes and reads, with fakes of the output ports (Annex C). */
class ShowtimeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123456789Z");
    private static final Instant NOW_IN_MILLIS = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MOVIE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ROOM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant START = Instant.parse("2026-11-01T20:00:00Z");
    private static final Instant END = Instant.parse("2026-11-01T22:00:00Z");
    private static final String KEY = "synthetic-key-0001";

    private final FakeShowtimes showtimes = new FakeShowtimes();
    private final FakeMovies movies = new FakeMovies();
    private final FakeRooms rooms = new FakeRooms();
    private final ShowtimeService service = new ShowtimeService(showtimes, movies, rooms, () -> ID,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Movie movieIn(PublicationStatus status) {
        MovieDetails details = new MovieDetails("Synthetic Movie", null, 120, "PG-13", null, null, null, null);
        return Movie.create(MOVIE_ID, details,
                status == PublicationStatus.ARCHIVED ? PublicationStatus.DRAFT : status, NOW).withStatus(status, NOW);
    }

    private static Room room() {
        RoomDetails details = new RoomDetails("Room 1", List.of(new Seat("A1", "A", 1, SeatType.STANDARD)));
        return Room.create(ROOM_ID, details, NOW);
    }

    private static CreateShowtimeInput create(String key) {
        return new CreateShowtimeInput(key, MOVIE_ID, ROOM_ID, START, END, 15000L, "en", "es");
    }

    private static ShowtimeChanges changes(Instant startsAt, Instant endsAt, Long price, ShowtimeStatus status) {
        return new ShowtimeChanges(startsAt, endsAt, price, null, null, status);
    }

    private static UpdateShowtimeInput update(String key, ShowtimeChanges changes) {
        return new UpdateShowtimeInput(key, ID, changes);
    }

    private void withPublishedMovieAndRoom() {
        movies.stored.put(MOVIE_ID, movieIn(PublicationStatus.PUBLISHED));
        rooms.stored.put(ROOM_ID, room());
    }

    private Showtime scheduled() {
        withPublishedMovieAndRoom();
        return service.createShowtime(create(KEY));
    }

    @Test
    void aCreatedShowtimeIsScheduledWithTheSnapshotsAndTheDatesOfTheClock() {
        withPublishedMovieAndRoom();

        Showtime showtime = service.createShowtime(create(KEY));

        assertEquals(ID, showtime.id());
        assertEquals(ShowtimeStatus.SCHEDULED, showtime.status());
        assertEquals("Synthetic Movie", showtime.movieTitleSnapshot());
        assertEquals("Room 1", showtime.roomNameSnapshot());
        assertEquals(START, showtime.interval().start());
        assertEquals(END, showtime.interval().end());
        assertEquals(15000L, showtime.priceCents());
        assertEquals("en", showtime.audioLanguage());
        assertEquals("es", showtime.subtitleLanguage());
        assertEquals(showtime, showtimes.stored.get(ID));
    }

    @Test
    void theTimestampsKeepMillisecondPrecision() {
        Showtime showtime = scheduled();

        assertEquals(NOW_IN_MILLIS, showtime.createdAt());
        assertEquals(NOW_IN_MILLIS, showtime.updatedAt());
    }

    @Test
    void theCreationWriteChecksTheOverlapAndCarriesTheEventOfTheCreationInstant() {
        scheduled();

        ShowtimeWrite write = showtimes.writes.get(0);
        assertEquals(WriteOperation.CREATE_SHOWTIME, write.operation());
        assertEquals(KEY, write.idempotencyKey());
        assertTrue(write.requestHash().matches("[0-9a-f]{64}"));
        assertTrue(write.checksOverlap());
        assertEquals(NOW_IN_MILLIS, write.eventToPublish().occurredAt());
        assertEquals(ID, write.eventToPublish().showtimeId());
        assertEquals("Synthetic Movie", write.eventToPublish().movieTitle());
    }

    @Test
    void aMovieThatDoesNotExistIsAReferenceErrorAndNothingIsSaved() {
        rooms.stored.put(ROOM_ID, room());

        ShowtimeReferenceException error = assertThrows(ShowtimeReferenceException.class,
                () -> service.createShowtime(create(KEY)));

        assertEquals("the movie does not exist", error.getMessage());
        assertTrue(showtimes.writes.isEmpty());
    }

    @Test
    void aRoomThatDoesNotExistIsAReferenceErrorAndNothingIsSaved() {
        movies.stored.put(MOVIE_ID, movieIn(PublicationStatus.PUBLISHED));

        ShowtimeReferenceException error = assertThrows(ShowtimeReferenceException.class,
                () -> service.createShowtime(create(KEY)));

        assertEquals("the room does not exist", error.getMessage());
        assertTrue(showtimes.writes.isEmpty());
    }

    @Test
    void aDraftOrArchivedMovieIsAReferenceErrorAndNothingIsSaved() {
        rooms.stored.put(ROOM_ID, room());
        for (PublicationStatus status : List.of(PublicationStatus.DRAFT, PublicationStatus.ARCHIVED)) {
            movies.stored.put(MOVIE_ID, movieIn(status));

            assertThrows(ShowtimeReferenceException.class, () -> service.createShowtime(create(KEY)));
        }
        assertTrue(showtimes.writes.isEmpty());
    }

    @Test
    void anArchivedRoomIsAReferenceErrorAndNothingIsSaved() {
        movies.stored.put(MOVIE_ID, movieIn(PublicationStatus.PUBLISHED));
        rooms.stored.put(ROOM_ID, room().archive(NOW));

        assertThrows(ShowtimeReferenceException.class, () -> service.createShowtime(create(KEY)));
        assertTrue(showtimes.writes.isEmpty());
    }

    @Test
    void anEndBeforeTheStartIsRejectedAndNothingIsSaved() {
        withPublishedMovieAndRoom();

        assertThrows(InvalidShowtimeException.class, () -> service.createShowtime(
                new CreateShowtimeInput(KEY, MOVIE_ID, ROOM_ID, END, START, 15000L, null, null)));
        assertTrue(showtimes.writes.isEmpty());
    }

    @Test
    void aNegativePriceIsRejectedAndNothingIsSaved() {
        withPublishedMovieAndRoom();

        assertThrows(InvalidShowtimeException.class, () -> service.createShowtime(
                new CreateShowtimeInput(KEY, MOVIE_ID, ROOM_ID, START, END, -1L, null, null)));
        assertTrue(showtimes.writes.isEmpty());
    }

    @Test
    void aReplayedCreationReturnsTheStoredShowtimeWithoutSavingOrReadingTheReferences() {
        Showtime first = scheduled();
        movies.stored.clear();
        rooms.stored.clear();
        movies.reads = 0;
        rooms.reads = 0;

        Showtime replay = service.createShowtime(create(KEY));

        assertEquals(first, replay);
        assertEquals(1, showtimes.writes.size());
        assertEquals(0, movies.reads);
        assertEquals(0, rooms.reads);
    }

    @Test
    void theSameKeyWithAnotherRequestPropagatesTheConflict() {
        scheduled();

        assertThrows(IdempotencyKeyConflictException.class, () -> service.createShowtime(
                new CreateShowtimeInput(KEY, MOVIE_ID, ROOM_ID, START, END, 20000L, "en", "es")));
    }

    @Test
    void aShowtimeIsReadById() {
        scheduled();

        assertEquals(ID, service.getShowtime(ID).id());
    }

    @Test
    void anUnknownShowtimeIsNotFound() {
        assertThrows(ShowtimeNotFoundException.class, () -> service.getShowtime(ID));
    }

    @Test
    void theListDelegatesWithTheWindowAndReportsTheTotal() {
        scheduled();

        ShowtimePage page = service.listShowtimes(2, 10);

        assertEquals(2, page.page());
        assertEquals(10, page.limit());
        assertEquals(1, page.total());
        assertEquals(1, page.items().size());
    }

    @Test
    void anUpdateChangesOnlyTheSentFieldsAndMovesTheModificationDate() {
        scheduled();
        ShowtimeService later = new ShowtimeService(showtimes, movies, rooms, () -> ID,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));

        Showtime updated = later.updateShowtime(update("synthetic-key-0002", changes(null, null, 9000L, null)));

        assertEquals(9000L, updated.priceCents());
        assertEquals(START, updated.interval().start());
        assertEquals("en", updated.audioLanguage());
        assertEquals(NOW_IN_MILLIS, updated.createdAt());
        assertEquals(NOW_IN_MILLIS.plusSeconds(60), updated.updatedAt());
        assertEquals(WriteOperation.UPDATE_SHOWTIME, showtimes.writes.get(1).operation());
    }

    @Test
    void anUpdateThatKeepsTheTimesDoesNotCheckTheOverlapAndCarriesNoEvent() {
        scheduled();

        service.updateShowtime(update("synthetic-key-0002", changes(null, null, 9000L, null)));
        service.updateShowtime(update("synthetic-key-0003", changes(START, END, null, null)));

        assertFalse(showtimes.writes.get(1).checksOverlap());
        assertFalse(showtimes.writes.get(2).checksOverlap());
        assertNull(showtimes.writes.get(1).eventToPublish());
    }

    @Test
    void anUpdateThatMovesTheTimesChecksTheOverlap() {
        scheduled();

        service.updateShowtime(update("synthetic-key-0002", changes(START.plusSeconds(3600), null, null, null)));

        assertTrue(showtimes.writes.get(1).checksOverlap());
        assertNull(showtimes.writes.get(1).eventToPublish());
    }

    @Test
    void anUpdateThatCompletesTheShowtimeDoesNotCheckTheOverlap() {
        scheduled();

        Showtime completed = service.updateShowtime(
                update("synthetic-key-0002", changes(null, null, null, ShowtimeStatus.COMPLETED)));

        assertEquals(ShowtimeStatus.COMPLETED, completed.status());
        assertFalse(showtimes.writes.get(1).checksOverlap());
    }

    @Test
    void aCancelledOrCompletedShowtimeCannotBeUpdated() {
        scheduled();
        service.cancelShowtime(new CancelShowtimeInput("synthetic-key-0002", ID));

        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateShowtime(update("synthetic-key-0003", changes(null, null, 1L, null))));

        Showtime stored = showtimes.stored.get(ID);
        showtimes.stored.put(ID, new Showtime(ID, MOVIE_ID, ROOM_ID, stored.movieTitleSnapshot(),
                stored.roomNameSnapshot(), stored.interval(), stored.priceCents(), stored.audioLanguage(),
                stored.subtitleLanguage(), ShowtimeStatus.COMPLETED, stored.createdAt(), stored.updatedAt()));
        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateShowtime(update("synthetic-key-0004", changes(null, null, 1L, null))));
    }

    @Test
    void aCancelledShowtimeCannotBecomeCompletedByAnUpdate() {
        scheduled();
        service.cancelShowtime(new CancelShowtimeInput("synthetic-key-0002", ID));

        assertThrows(InvalidStatusTransitionException.class, () -> service.updateShowtime(
                update("synthetic-key-0003", changes(null, null, null, ShowtimeStatus.COMPLETED))));
        assertEquals(2, showtimes.writes.size());
    }

    @Test
    void anUpdateOfAnUnknownShowtimeIsNotFound() {
        assertThrows(ShowtimeNotFoundException.class,
                () -> service.updateShowtime(update(KEY, changes(null, null, 1L, null))));
    }

    @Test
    void aReplayedUpdateAnswersWithoutSavingAgain() {
        scheduled();
        UpdateShowtimeInput input = update("synthetic-key-0002", changes(null, null, 9000L, null));
        Showtime updated = service.updateShowtime(input);

        Showtime replay = service.updateShowtime(input);

        assertEquals(updated, replay);
        assertEquals(2, showtimes.writes.size());
    }

    @Test
    void cancellingStoresACancelledShowtimeWithoutOverlapCheckOrEvent() {
        scheduled();

        service.cancelShowtime(new CancelShowtimeInput("synthetic-key-0002", ID));

        assertEquals(ShowtimeStatus.CANCELLED, showtimes.stored.get(ID).status());
        ShowtimeWrite write = showtimes.writes.get(1);
        assertEquals(WriteOperation.CANCEL_SHOWTIME, write.operation());
        assertFalse(write.checksOverlap());
        assertNull(write.eventToPublish());
        assertEquals(NOW_IN_MILLIS, showtimes.stored.get(ID).updatedAt());
    }

    @Test
    void cancellingTwiceWithAnotherKeyIsAnInvalidTransition() {
        scheduled();
        service.cancelShowtime(new CancelShowtimeInput("synthetic-key-0002", ID));

        assertThrows(InvalidStatusTransitionException.class,
                () -> service.cancelShowtime(new CancelShowtimeInput("synthetic-key-0003", ID)));
    }

    @Test
    void aReplayedCancelAnswersWithoutLoadingTheShowtime() {
        scheduled();
        CancelShowtimeInput cancel = new CancelShowtimeInput("synthetic-key-0002", ID);
        service.cancelShowtime(cancel);
        showtimes.reads = 0;

        service.cancelShowtime(cancel);

        assertEquals(0, showtimes.reads);
        assertEquals(2, showtimes.writes.size());
    }

    @Test
    void aCancelOfAnUnknownShowtimeIsNotFound() {
        assertThrows(ShowtimeNotFoundException.class,
                () -> service.cancelShowtime(new CancelShowtimeInput(KEY, ID)));
    }

    /** In-memory output port: it keeps the showtimes and the keys the way the database would. */
    private static final class FakeShowtimes implements ShowtimeRepository {

        final Map<UUID, Showtime> stored = new HashMap<>();
        final List<ShowtimeWrite> writes = new ArrayList<>();
        int reads;
        private final Map<String, String> hashByKey = new HashMap<>();
        private final Map<String, UUID> resourceByKey = new HashMap<>();

        @Override
        public Optional<UUID> findIdempotentResource(String key, String requestHash) {
            String known = hashByKey.get(key);
            if (known == null) {
                return Optional.empty();
            }
            if (!known.equals(requestHash)) {
                throw new IdempotencyKeyConflictException();
            }
            return Optional.of(resourceByKey.get(key));
        }

        @Override
        public Optional<Showtime> findById(UUID id) {
            reads++;
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public ShowtimePage findAll(int page, int limit) {
            List<Showtime> all = stored.values().stream()
                    .sorted(Comparator.comparing(showtime -> showtime.interval().start())).toList();
            return new ShowtimePage(all, page, limit, all.size());
        }

        @Override
        public Showtime save(ShowtimeWrite write) {
            stored.put(write.showtime().id(), write.showtime());
            hashByKey.put(write.idempotencyKey(), write.requestHash());
            resourceByKey.put(write.idempotencyKey(), write.showtime().id());
            writes.add(write);
            return write.showtime();
        }
    }

    /** In-memory movies: only the lookup by id is used by the showtime use cases. */
    private static final class FakeMovies implements MovieRepository {

        final Map<UUID, Movie> stored = new HashMap<>();
        int reads;

        @Override
        public Optional<UUID> findIdempotentResource(String key, String requestHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Movie> findById(UUID id) {
            reads++;
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public MoviePage findActive(int page, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Movie save(MovieWrite write) {
            throw new UnsupportedOperationException();
        }
    }

    /** In-memory rooms: only the lookup by id is used by the showtime use cases. */
    private static final class FakeRooms implements RoomRepository {

        final Map<UUID, Room> stored = new HashMap<>();
        int reads;

        @Override
        public Optional<UUID> findIdempotentResource(String key, String requestHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Room> findById(UUID id) {
            reads++;
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public RoomPage findActive(int page, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Room save(RoomWrite write) {
            throw new UnsupportedOperationException();
        }
    }
}
