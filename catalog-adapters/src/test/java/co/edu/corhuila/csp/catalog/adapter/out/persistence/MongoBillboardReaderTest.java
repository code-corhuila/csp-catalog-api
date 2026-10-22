package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.csp.catalog.application.port.in.BillboardQuery;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardEntry;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardMovie;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardPage;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardRoom;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardShowtime;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import co.edu.corhuila.csp.catalog.domain.model.TimeInterval;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * The billboard reader against a real MongoDB, as a single-node replica set with the schema of
 * csp-catalog-db (Annex C): the validators judge every document the tests insert. The tests run when
 * TEST_MONGODB_URI points to a disposable database, and they empty its movies, rooms and showtimes. Without
 * it they are skipped on a developer machine and fail when CI is set, so a green CI ran them.
 */
class MongoBillboardReaderTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-10-25");
    private static final Instant DAY_START = Instant.parse("2026-10-25T00:00:00Z");
    private static final Instant EVENING = Instant.parse("2026-10-25T20:00:00Z");
    private static final int ROOMS_AND_SHOWTIMES_PER_TEST = 8;

    private static MongoTemplate template;
    private final MongoBillboardReader reader = new MongoBillboardReader(template);
    private Movie movie;
    private Room room;

    @BeforeAll
    static void connect() {
        String uri = System.getenv("TEST_MONGODB_URI");
        boolean configured = uri != null && !uri.isBlank();
        if (!configured && "true".equals(System.getenv("CI"))) {
            fail("TEST_MONGODB_URI is required in CI: a green build must mean the MongoDB tests ran");
        }
        assumeTrue(configured, "TEST_MONGODB_URI is not set: the MongoDB integration tests were skipped");
        template = new MongoTemplate(new SimpleMongoClientDatabaseFactory(uri));
    }

    @BeforeEach
    void emptyTheCollectionsAndStoreAMovieAndARoom() {
        for (String collection : List.of("movies", "rooms", "showtimes")) {
            template.getCollection(collection).deleteMany(new Document());
        }
        movie = storedMovie("Synthetic Movie", PublicationStatus.PUBLISHED);
        room = storedRoom("Synthetic Room");
    }

    private static Movie storedMovie(String title, PublicationStatus status) {
        Movie created = Movie.create(UUID.randomUUID(), new MovieDetails(title, "A synthetic synopsis.", 120, "PG-13",
                LocalDate.parse("2026-09-01"), List.of("Drama", "Comedy"), List.of("Synthetic Actor"),
                URI.create("https://example.com/poster.png")), PublicationStatus.DRAFT, NOW);
        Movie stored = status == PublicationStatus.DRAFT ? created : created.withStatus(status, NOW);
        template.insert(MovieDocumentMapper.toDocument(stored), "movies");
        return stored;
    }

    private static Room storedRoom(String name) {
        Room created = Room.create(UUID.randomUUID(), new RoomDetails(name, List.of(
                new Seat("A1", "A", 1, SeatType.STANDARD), new Seat("A2", "A", 2, SeatType.VIP))), NOW);
        template.insert(RoomDocumentMapper.toDocument(created), "rooms");
        return created;
    }

    private static Showtime storedShowtime(UUID id, Movie of, Room in, Instant start, Instant end,
            ShowtimeStatus status) {
        Showtime showtime = new Showtime(id, of.id(), in.id(), of.details().title(), in.details().name(),
                new TimeInterval(start, end), 15000, "EN", "ES", status, NOW, NOW);
        template.insert(ShowtimeDocumentMapper.toDocument(showtime), "showtimes");
        return showtime;
    }

    private static void update(String collection, UUID id, Update change) {
        template.updateFirst(Query.query(Criteria.where("_id").is(id.toString())), change, collection);
    }

    private Showtime scheduled(Instant start, Instant end) {
        return storedShowtime(UUID.randomUUID(), movie, room, start, end, ShowtimeStatus.SCHEDULED);
    }

    private Showtime scheduled(UUID id, Instant start) {
        return storedShowtime(id, movie, room, start, start.plusSeconds(3600), ShowtimeStatus.SCHEDULED);
    }

    private BillboardPage find(LocalDate date, UUID movieId, int page, int limit) {
        return reader.find(new BillboardQuery(date, movieId, page, limit), NOW);
    }

    private BillboardPage findAll() {
        return find(null, null, 1, 100);
    }

    private static List<UUID> ids(BillboardPage page) {
        return page.items().stream().map(entry -> entry.showtime().id()).toList();
    }

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-4000-8000-00000000000" + n);
    }

    @Test
    void aScheduledShowtimeOfAPublishedMovieInAnActiveRoomIsListedWithEveryPublicField() {
        Showtime showtime = scheduled(EVENING, EVENING.plusSeconds(7200));

        BillboardPage page = findAll();

        assertEquals(1, page.total());
        assertEquals(1, page.page());
        assertEquals(100, page.limit());
        BillboardEntry entry = page.items().get(0);
        assertEquals(new BillboardMovie(movie.id(), "Synthetic Movie", "A synthetic synopsis.", 120,
                List.of("Drama", "Comedy"), "PG-13", URI.create("https://example.com/poster.png"),
                LocalDate.parse("2026-09-01")), entry.movie());
        assertEquals(new BillboardRoom(room.id(), "Synthetic Room", 2), entry.room());
        assertEquals(new BillboardShowtime(showtime.id(), EVENING, EVENING.plusSeconds(7200), 15000, "EN", "ES"),
                entry.showtime());
    }

    @Test
    void aDraftMovieAndAnArchivedMovieNeverAppear() {
        Movie draft = storedMovie("Draft Movie", PublicationStatus.DRAFT);
        Movie archived = storedMovie("Archived Movie", PublicationStatus.PUBLISHED);
        update("movies", archived.id(), new Update().set("publication_status", "ARCHIVED").set("deleted_at", Date.from(NOW)));
        storedShowtime(UUID.randomUUID(), draft, room, EVENING, EVENING.plusSeconds(3600), ShowtimeStatus.SCHEDULED);
        storedShowtime(UUID.randomUUID(), archived, room, EVENING, EVENING.plusSeconds(3600), ShowtimeStatus.SCHEDULED);
        Showtime visible = scheduled(EVENING, EVENING.plusSeconds(3600));

        assertEquals(List.of(visible.id()), ids(findAll()));
        assertEquals(1, findAll().total());
    }

    @Test
    void aShowtimeOfAnArchivedRoomIsHidden() {
        Room archived = storedRoom("Archived Room");
        update("rooms", archived.id(), new Update().set("deleted_at", Date.from(NOW)));
        storedShowtime(UUID.randomUUID(), movie, archived, EVENING, EVENING.plusSeconds(3600),
                ShowtimeStatus.SCHEDULED);
        Showtime visible = scheduled(EVENING, EVENING.plusSeconds(3600));

        assertEquals(List.of(visible.id()), ids(findAll()));
    }

    @Test
    void cancelledAndCompletedShowtimesNeverAppear() {
        storedShowtime(UUID.randomUUID(), movie, room, EVENING, EVENING.plusSeconds(3600), ShowtimeStatus.CANCELLED);
        storedShowtime(UUID.randomUUID(), movie, room, EVENING, EVENING.plusSeconds(3600), ShowtimeStatus.COMPLETED);
        Showtime visible = scheduled(EVENING, EVENING.plusSeconds(3600));

        assertEquals(List.of(visible.id()), ids(findAll()));
    }

    @Test
    void aShowtimeStillRunningIsListedAndOneThatEndsAtTheInstantOfTheQueryIsNot() {
        Instant started = NOW.minusSeconds(3600);
        scheduled(started, NOW);
        Showtime endsOneMillisecondLater = scheduled(started, NOW.plusMillis(1));
        Showtime running = scheduled(NOW.minusSeconds(60), NOW.plusSeconds(3600));
        scheduled(started, NOW.minusMillis(1));

        BillboardPage page = findAll();

        assertEquals(List.of(endsOneMillisecondLater.id(), running.id()), ids(page));
        assertEquals(2, page.total());
    }

    @Test
    void theDateKeepsExactlyTheShowtimesThatStartInThatUtcDay() {
        scheduled(DAY_START.minusMillis(1), DAY_START.plusSeconds(3600));
        Showtime atMidnight = scheduled(DAY_START, DAY_START.plusSeconds(3600));
        Showtime lastMillisecond = scheduled(DAY_START.plusSeconds(86400).minusMillis(1),
                DAY_START.plusSeconds(90000));
        scheduled(DAY_START.plusSeconds(86400), DAY_START.plusSeconds(90000));

        BillboardPage page = find(DAY, null, 1, 20);

        assertEquals(List.of(atMidnight.id(), lastMillisecond.id()), ids(page));
        assertEquals(2, page.total());
    }

    @Test
    void theMovieFilterKeepsOnlyTheShowtimesOfThatMovie() {
        Movie other = storedMovie("Other Movie", PublicationStatus.PUBLISHED);
        storedShowtime(UUID.randomUUID(), other, room, EVENING, EVENING.plusSeconds(3600), ShowtimeStatus.SCHEDULED);
        Showtime wanted = scheduled(EVENING, EVENING.plusSeconds(3600));

        BillboardPage page = find(null, movie.id(), 1, 20);

        assertEquals(List.of(wanted.id()), ids(page));
        assertEquals(1, page.total());
    }

    @Test
    void theDateAndTheMovieFiltersApplyTogether() {
        Movie other = storedMovie("Other Movie", PublicationStatus.PUBLISHED);
        storedShowtime(UUID.randomUUID(), other, room, EVENING, EVENING.plusSeconds(3600), ShowtimeStatus.SCHEDULED);
        scheduled(EVENING.plusSeconds(86400), EVENING.plusSeconds(90000));
        Showtime wanted = scheduled(EVENING, EVENING.plusSeconds(3600));

        assertEquals(List.of(wanted.id()), ids(find(DAY, movie.id(), 1, 20)));
    }

    @Test
    void theShowtimesAreOrderedByStartAndThenById() {
        scheduled(id(3), EVENING);
        scheduled(id(1), EVENING);
        scheduled(id(2), EVENING.minusSeconds(60));

        assertEquals(List.of(id(2), id(1), id(3)), ids(findAll()));
    }

    @Test
    void theWindowsOfThePaginationCoverTheListAndTheTotalIsTheSameOnEveryPage() {
        for (int n = 1; n <= 5; n++) {
            scheduled(id(n), EVENING.plusSeconds(n * 60L));
        }

        BillboardPage first = find(null, null, 1, 2);
        BillboardPage second = find(null, null, 2, 2);
        BillboardPage third = find(null, null, 3, 2);
        BillboardPage beyond = find(null, null, 4, 2);

        assertEquals(List.of(id(1), id(2)), ids(first));
        assertEquals(List.of(id(3), id(4)), ids(second));
        assertEquals(List.of(id(5)), ids(third));
        assertTrue(beyond.items().isEmpty());
        for (BillboardPage page : List.of(first, second, third, beyond)) {
            assertEquals(5, page.total());
            assertEquals(2, page.limit());
        }
        assertEquals(4, beyond.page());
    }

    @Test
    void anEmptyDatabaseGivesAnEmptyPageWithATotalOfZero() {
        BillboardPage page = findAll();

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.total());
    }

    @Test
    void readingWritesNothing() {
        scheduled(EVENING, EVENING.plusSeconds(3600));
        long movies = template.getCollection("movies").countDocuments();
        long rooms = template.getCollection("rooms").countDocuments();
        long showtimes = template.getCollection("showtimes").countDocuments();

        findAll();
        find(DAY, movie.id(), 1, 5);

        assertEquals(movies, template.getCollection("movies").countDocuments());
        assertEquals(rooms, template.getCollection("rooms").countDocuments());
        assertEquals(showtimes, template.getCollection("showtimes").countDocuments());
    }

    @Test
    void theFirstStageOfTheQueryUsesTheIndexOfStatusAndStart() {
        for (int n = 0; n < ROOMS_AND_SHOWTIMES_PER_TEST; n++) {
            scheduled(EVENING.plusSeconds(n * 60L), EVENING.plusSeconds(3600 + n * 60L));
        }
        List<Document> pipeline = MongoBillboardReader.pipeline(new BillboardQuery(DAY, movie.id(), 1, 20), NOW);

        Document explained = template.getDb().runCommand(new Document("explain", new Document("aggregate", "showtimes")
                .append("pipeline", pipeline).append("cursor", new Document())).append("verbosity", "queryPlanner"));

        assertTrue(explained.toJson().contains("idx_showtimes_status_starts_at"), explained.toJson());
    }
}
