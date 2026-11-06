package co.edu.corhuila.csp.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.csp.catalog.application.port.in.ArchiveMovieInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateMovieInput;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateMovieInput;
import co.edu.corhuila.csp.catalog.application.port.out.IdGenerator;
import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.port.out.MovieWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.InvalidMovieException;
import co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieChanges;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.MovieNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The use cases of the movie writes and reads, with a fake of the output port (Annex C). */
class MovieServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123456789Z");
    private static final Instant NOW_IN_MILLIS = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String KEY = "synthetic-key-0001";

    private final FakeMovies movies = new FakeMovies();
    private final MovieService service = new MovieService(movies, () -> ID, Clock.fixed(NOW, ZoneOffset.UTC));

    private static MovieDetails details() {
        return new MovieDetails("Synthetic Movie", null, 120, "PG-13", null, null, null, null);
    }

    private static MovieChanges titleChange(String title) {
        return new MovieChanges(title, null, null, null, null, null, null, null, null);
    }

    @Test
    void aCreatedMovieTakesItsIdAndDatesFromThePortsAtTheMillisecondTheDatabaseKeeps() {
        Movie movie = service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));

        assertEquals(ID, movie.id());
        assertEquals(PublicationStatus.DRAFT, movie.status());
        assertEquals(NOW_IN_MILLIS, movie.createdAt());
        assertEquals(NOW_IN_MILLIS, movie.updatedAt());
        assertEquals(movie, movies.stored.get(ID));
    }

    @Test
    void theWriteCarriesItsKeyItsOperationAndAHashOfTheRequest() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));

        MovieWrite write = movies.writes.get(0);
        assertEquals(KEY, write.idempotencyKey());
        assertEquals(WriteOperation.CREATE_MOVIE, write.operation());
        assertEquals(64, write.requestHash().length());
        assertTrue(write.requestHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void theSameRequestHasTheSameHashAndAnotherRequestHasAnother() {
        service.createMovie(new CreateMovieInput("synthetic-key-0001", details(), PublicationStatus.DRAFT));
        service.createMovie(new CreateMovieInput("synthetic-key-0002", details(), PublicationStatus.DRAFT));
        service.createMovie(new CreateMovieInput("synthetic-key-0003",
                new MovieDetails("Another Movie", null, 120, "PG-13", null, null, null, null), PublicationStatus.DRAFT));

        assertEquals(movies.writes.get(0).requestHash(), movies.writes.get(1).requestHash());
        assertNotEquals(movies.writes.get(0).requestHash(), movies.writes.get(2).requestHash());
    }

    @Test
    void aReplayedCreationReturnsTheStoredMovieAndWritesNothingMore() {
        Movie first = service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));

        Movie replay = service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));

        assertEquals(first, replay);
        assertEquals(1, movies.writes.size());
    }

    @Test
    void theSameKeyWithAnotherRequestIsAConflict() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));

        assertThrows(IdempotencyKeyConflictException.class, () -> service.createMovie(new CreateMovieInput(KEY,
                new MovieDetails("Another Movie", null, 120, "PG-13", null, null, null, null), PublicationStatus.DRAFT)));
    }

    @Test
    void aBrokenInvariantStopsTheCreationBeforeAnythingIsWritten() {
        assertThrows(InvalidMovieException.class, () -> service.createMovie(new CreateMovieInput(KEY,
                new MovieDetails("Synthetic Movie", null, 0, "PG-13", null, null, null, null), PublicationStatus.DRAFT)));
        assertTrue(movies.writes.isEmpty());
    }

    @Test
    void aMovieCannotBeCreatedArchived() {
        assertThrows(InvalidStatusTransitionException.class,
                () -> service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.ARCHIVED)));
        assertTrue(movies.writes.isEmpty());
    }

    @Test
    void aMovieIsReadById() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));

        assertEquals(ID, service.getMovie(ID).id());
    }

    @Test
    void anUnknownMovieIsNotFound() {
        assertThrows(MovieNotFoundException.class, () -> service.getMovie(ID));
    }

    @Test
    void anArchivedMovieIsStillReadById() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));
        service.archiveMovie(new ArchiveMovieInput("synthetic-key-0002", ID));

        assertEquals(PublicationStatus.ARCHIVED, service.getMovie(ID).status());
    }

    @Test
    void theListAsksThePortForTheActiveMoviesOfThePage() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));

        MoviePage page = service.listMovies(2, 10);

        assertEquals(2, page.page());
        assertEquals(10, page.limit());
        assertEquals(1, page.total());
    }

    @Test
    void anUpdateAppliesTheChangesAndMovesTheModificationDate() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));
        MovieService later = new MovieService(movies, () -> ID,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));

        Movie updated = later.updateMovie(new UpdateMovieInput("synthetic-key-0002", ID, titleChange("New title")));

        assertEquals("New title", updated.details().title());
        assertEquals(NOW_IN_MILLIS.plusSeconds(60), updated.updatedAt());
        assertEquals(WriteOperation.UPDATE_MOVIE, movies.writes.get(1).operation());
    }

    @Test
    void anUpdateOfAnUnknownMovieIsNotFound() {
        assertThrows(MovieNotFoundException.class,
                () -> service.updateMovie(new UpdateMovieInput(KEY, ID, titleChange("New title"))));
    }

    @Test
    void aReplayedUpdateAnswersBeforeTheRulesOfTheStateAreEvaluated() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));
        UpdateMovieInput publish = new UpdateMovieInput("synthetic-key-0002", ID,
                new MovieChanges(null, null, null, null, null, null, null, null, PublicationStatus.PUBLISHED));
        service.updateMovie(publish);
        service.archiveMovie(new ArchiveMovieInput("synthetic-key-0003", ID));

        Movie replay = service.updateMovie(publish);

        assertEquals(PublicationStatus.ARCHIVED, replay.status());
        assertEquals(3, movies.writes.size());
    }

    @Test
    void anUpdateOfAnArchivedMovieIsRejected() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));
        service.archiveMovie(new ArchiveMovieInput("synthetic-key-0002", ID));

        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateMovie(new UpdateMovieInput("synthetic-key-0003", ID, titleChange("New title"))));
    }

    @Test
    void archivingSetsTheDeletionDateAndKeepsTheMovie() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.PUBLISHED));

        service.archiveMovie(new ArchiveMovieInput("synthetic-key-0002", ID));

        Movie archived = movies.stored.get(ID);
        assertEquals(PublicationStatus.ARCHIVED, archived.status());
        assertEquals(NOW_IN_MILLIS, archived.deletedAt());
        assertEquals(WriteOperation.ARCHIVE_MOVIE, movies.writes.get(1).operation());
    }

    @Test
    void aReplayedArchiveWritesNothingMoreAndAnArchiveOfAnUnknownMovieIsNotFound() {
        service.createMovie(new CreateMovieInput(KEY, details(), PublicationStatus.DRAFT));
        ArchiveMovieInput archive = new ArchiveMovieInput("synthetic-key-0002", ID);
        service.archiveMovie(archive);

        service.archiveMovie(archive);

        assertEquals(2, movies.writes.size());
        assertThrows(MovieNotFoundException.class, () -> new MovieService(new FakeMovies(), () -> ID,
                Clock.fixed(NOW, ZoneOffset.UTC)).archiveMovie(new ArchiveMovieInput(KEY, ID)));
    }

    /** In-memory output port: it keeps the movies and the keys the way the database would. */
    private static final class FakeMovies implements MovieRepository {

        final Map<UUID, Movie> stored = new HashMap<>();
        final List<MovieWrite> writes = new ArrayList<>();
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
        public Optional<Movie> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public MoviePage findActive(int page, int limit) {
            List<Movie> active = stored.values().stream().filter(movie -> !movie.isArchived()).toList();
            return new MoviePage(active, page, limit, active.size());
        }

        @Override
        public Movie save(MovieWrite write) {
            stored.put(write.movie().id(), write.movie());
            hashByKey.put(write.idempotencyKey(), write.requestHash());
            resourceByKey.put(write.idempotencyKey(), write.movie().id());
            writes.add(write);
            return write.movie();
        }
    }
}
