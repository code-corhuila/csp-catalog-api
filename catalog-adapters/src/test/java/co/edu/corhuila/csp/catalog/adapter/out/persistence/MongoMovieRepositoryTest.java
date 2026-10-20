package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.MovieOperation;
import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieWrite;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieChanges;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * The adapter against a real MongoDB, as a single-node replica set with the schema of csp-catalog-db
 * (Annex C): the validator of the collection judges every document the adapter writes. The tests run
 * when TEST_MONGODB_URI points to a disposable database, and they empty its movies and idempotency_key.
 * Without it they are skipped on a developer machine and fail when CI is set, so a green CI ran them.
 */
class MongoMovieRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);

    private static MongoTemplate template;
    private final MongoMovieRepository repository = new MongoMovieRepository(template);

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
    void emptyTheCollections() {
        template.getCollection("movies").deleteMany(new org.bson.Document());
        template.getCollection("idempotency_key").deleteMany(new org.bson.Document());
    }

    private static Movie movie(String title, PublicationStatus status, Instant at) {
        return Movie.create(UUID.randomUUID(), new MovieDetails(title, "A synopsis", 120, "PG-13",
                LocalDate.of(2026, 12, 1), List.of("Drama"), List.of("Synthetic Actor"),
                URI.create("https://example.com/poster.png")), status, at);
    }

    private static MovieWrite create(Movie movie, String key, String hash) {
        return new MovieWrite(movie, MovieOperation.CREATE_MOVIE, key, hash);
    }

    @Test
    void aSavedMovieIsReadBackWithEveryField() {
        Movie movie = movie("Synthetic Movie", PublicationStatus.PUBLISHED, NOW);

        Movie saved = repository.save(create(movie, "synthetic-key-0001", HASH));

        assertEquals(movie, saved);
        assertEquals(movie, repository.findById(movie.id()).orElseThrow());
    }

    @Test
    void anUnknownMovieIsNotFound() {
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void theKeyOfAWriteFindsTheMovieItProducedForTheSameRequest() {
        Movie movie = movie("Synthetic Movie", PublicationStatus.DRAFT, NOW);
        repository.save(create(movie, "synthetic-key-0001", HASH));

        assertEquals(movie.id(), repository.findIdempotentResource("synthetic-key-0001", HASH).orElseThrow());
        assertTrue(repository.findIdempotentResource("synthetic-key-9999", HASH).isEmpty());
    }

    @Test
    void theSameKeyWithAnotherRequestIsAConflict() {
        repository.save(create(movie("Synthetic Movie", PublicationStatus.DRAFT, NOW), "synthetic-key-0001", HASH));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> repository.findIdempotentResource("synthetic-key-0001", OTHER_HASH));
    }

    @Test
    void theMovieIsNotWrittenWhenItsKeyCannotBe() {
        repository.save(create(movie("First Movie", PublicationStatus.DRAFT, NOW), "synthetic-key-0001", HASH));
        Movie second = movie("Second Movie", PublicationStatus.DRAFT, NOW);

        assertThrows(IdempotencyKeyConflictException.class,
                () -> repository.save(create(second, "synthetic-key-0001", OTHER_HASH)));

        assertTrue(repository.findById(second.id()).isEmpty());
    }

    @Test
    void aConcurrentReplayReturnsTheMovieTheOtherCallStored() throws Exception {
        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Movie>> results = IntStream.range(0, callers)
                .mapToObj(i -> pool.submit(() -> {
                    start.await();
                    return repository.save(create(movie("Synthetic Movie", PublicationStatus.DRAFT, NOW),
                            "synthetic-key-race", HASH));
                }))
                .toList();
        start.countDown();

        UUID winner = results.get(0).get().id();
        for (Future<Movie> result : results) {
            assertEquals(winner, result.get().id());
        }
        pool.shutdown();
        assertEquals(1, template.getCollection("movies").countDocuments());
        assertEquals(1, template.getCollection("idempotency_key").countDocuments());
    }

    @Test
    void anUpdateReplacesTheMovieAndKeepsOneRecordOfIt() {
        Movie movie = movie("Synthetic Movie", PublicationStatus.DRAFT, NOW);
        repository.save(create(movie, "synthetic-key-0001", HASH));
        Movie changed = movie.change(new MovieChanges("New title", null, null, null, null, null, null, null,
                PublicationStatus.PUBLISHED), NOW.plusSeconds(60));

        repository.save(new MovieWrite(changed, MovieOperation.UPDATE_MOVIE, "synthetic-key-0002", OTHER_HASH));

        assertEquals(changed, repository.findById(movie.id()).orElseThrow());
        assertEquals(1, template.getCollection("movies").countDocuments());
    }

    @Test
    void anArchivedMovieIsStoredWithItsDeletionDateAndIsStillReadById() {
        Movie movie = movie("Synthetic Movie", PublicationStatus.PUBLISHED, NOW);
        repository.save(create(movie, "synthetic-key-0001", HASH));
        Movie archived = movie.archive(NOW.plusSeconds(60));

        repository.save(new MovieWrite(archived, MovieOperation.ARCHIVE_MOVIE, "synthetic-key-0002", OTHER_HASH));

        assertEquals(archived, repository.findById(movie.id()).orElseThrow());
        assertEquals(NOW.plusSeconds(60), repository.findById(movie.id()).orElseThrow().deletedAt());
    }

    @Test
    void theListHoldsTheMoviesThatAreNotArchivedNewestFirst() {
        Movie oldest = movie("Oldest", PublicationStatus.DRAFT, NOW);
        Movie newest = movie("Newest", PublicationStatus.PUBLISHED, NOW.plusSeconds(120));
        Movie archived = movie("Archived", PublicationStatus.DRAFT, NOW.plusSeconds(60)).archive(NOW.plusSeconds(180));
        repository.save(create(oldest, "synthetic-key-0001", HASH));
        repository.save(create(newest, "synthetic-key-0002", "1".repeat(64)));
        repository.save(create(archived, "synthetic-key-0003", "2".repeat(64)));

        MoviePage page = repository.findActive(1, 20);

        assertEquals(List.of(newest, oldest), page.items());
        assertEquals(2, page.total());
        assertEquals(1, page.page());
        assertEquals(20, page.limit());
    }

    @Test
    void theListIsCutInTheWindowOfThePageAndReportsTheTotal() {
        for (int i = 0; i < 5; i++) {
            repository.save(create(movie("Movie " + i, PublicationStatus.DRAFT, NOW.plusSeconds(i)),
                    "synthetic-key-000" + i, String.valueOf(i).repeat(64)));
        }

        MoviePage second = repository.findActive(2, 2);

        assertEquals(2, second.items().size());
        assertEquals("Movie 2", second.items().get(0).details().title());
        assertEquals("Movie 1", second.items().get(1).details().title());
        assertEquals(5, second.total());
        assertTrue(repository.findActive(4, 2).items().isEmpty());
    }

    @Test
    void aWindowOutsideTheBoundsOfTheContractIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> repository.findActive(0, 20));
        assertThrows(IllegalArgumentException.class, () -> repository.findActive(1, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.findActive(1, 101));
        assertFalse(repository.findActive(1, 100).items().iterator().hasNext());
    }
}
