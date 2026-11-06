package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchQuery;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardMovie;
import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchPage;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
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

/**
 * The movie search reader against a real MongoDB, as a single-node replica set with the schema of
 * csp-catalog-db (Annex C): the validator judges every document the tests insert and the text index
 * {@code idx_movies_title_synopsis_text} answers the text filter. The tests run when TEST_MONGODB_URI points to a
 * disposable database, and they empty its movies. Without it they are skipped on a developer machine and fail when
 * CI is set, so a green CI ran them.
 */
class MongoMovieSearchReaderTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private static MongoTemplate template;
    private final MongoMovieSearchReader reader = new MongoMovieSearchReader(template);

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
    void emptyTheMovies() {
        template.getCollection("movies").deleteMany(new Document());
    }

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-4000-8000-00000000000" + n);
    }

    private static Movie store(UUID id, String title, String synopsis, List<String> genres, int minutes,
            PublicationStatus status) {
        Movie created = Movie.create(id, new MovieDetails(title, synopsis, minutes, "PG-13",
                LocalDate.parse("2026-09-01"), genres, List.of("Synthetic Actor"),
                URI.create("https://example.com/poster.png")), PublicationStatus.DRAFT, NOW);
        Movie stored = status == PublicationStatus.DRAFT ? created : created.withStatus(status, NOW);
        template.insert(MovieDocumentMapper.toDocument(stored), "movies");
        return stored;
    }

    private static Movie published(int n, String title, String synopsis, List<String> genres, int minutes) {
        return store(id(n), title, synopsis, genres, minutes, PublicationStatus.PUBLISHED);
    }

    private MovieSearchPage find(String text, String genre, Integer durationMax, int page, int limit) {
        return reader.find(new MovieSearchQuery(text, genre, durationMax, page, limit));
    }

    private MovieSearchPage findAll() {
        return find(null, null, null, 1, 100);
    }

    private MovieSearchPage findText(String text) {
        return find(text, null, null, 1, 100);
    }

    private static List<UUID> ids(MovieSearchPage page) {
        return page.items().stream().map(BillboardMovie::id).toList();
    }

    @Test
    void aPublishedMovieIsListedWithEveryPublicFieldAndNoDraftOrArchivedMovieIs() {
        Movie movie = published(1, "Synthetic Movie", "A synthetic synopsis.", List.of("Drama", "Comedy"), 120);
        store(id(2), "Draft Movie", "Not published.", List.of("Drama"), 100, PublicationStatus.DRAFT);
        store(id(3), "Archived Movie", "No longer shown.", List.of("Drama"), 100, PublicationStatus.ARCHIVED);

        MovieSearchPage page = findAll();

        assertEquals(1, page.total());
        assertEquals(1, page.page());
        assertEquals(100, page.limit());
        assertEquals(new BillboardMovie(movie.id(), "Synthetic Movie", "A synthetic synopsis.", 120,
                List.of("Drama", "Comedy"), "PG-13", URI.create("https://example.com/poster.png"),
                LocalDate.parse("2026-09-01")), page.items().get(0));
    }

    @Test
    void aPublishedMovieWithADeletionDateIsNotListed() {
        published(1, "Still Shown", "A movie.", List.of("Drama"), 100);
        published(2, "Deleted Later", "A movie.", List.of("Drama"), 100);
        template.getCollection("movies").updateOne(new Document("_id", id(2).toString()),
                new Document("$set", new Document("deleted_at", Date.from(NOW))));

        assertEquals(List.of(id(1)), ids(findAll()));
        assertEquals(1, findAll().total());
    }

    @Test
    void aPublishedMovieWithNoShowtimeIsListed() {
        template.getCollection("showtimes").deleteMany(new Document());
        published(1, "Synthetic Movie", "A synthetic synopsis.", List.of("Drama"), 120);

        assertEquals(List.of(id(1)), ids(findAll()));
    }

    @Test
    void theMoviesAreOrderedByTitleAndThenById() {
        published(3, "Beta", "Second.", List.of("Drama"), 100);
        published(2, "Alpha", "First, with a higher id than its twin.", List.of("Drama"), 100);
        published(1, "Alpha", "First.", List.of("Drama"), 100);

        assertEquals(List.of(id(1), id(2), id(3)), ids(findAll()));
    }

    @Test
    void aTextQueryFindsAWholeWordOfTheTitleOrOfTheSynopsis() {
        published(1, "Space Action", "A pilot crosses the galaxy.", List.of("Drama"), 110);
        published(2, "Quiet Garden", "An action of kindness in a garden.", List.of("Drama"), 95);
        published(3, "Galaxy Quest", "A crew.", List.of("Drama"), 150);

        assertEquals(List.of(id(2), id(1)), ids(findText("action")));
        assertEquals(List.of(id(3), id(1)), ids(findText("galaxy")));
    }

    @Test
    void aTextQueryIgnoresCaseAndAccents() {
        published(1, "Acción Total", "Una historia.", List.of("Drama"), 110);

        assertEquals(List.of(id(1)), ids(findText("ACCION")));
        assertEquals(List.of(id(1)), ids(findText("acción")));
    }

    @Test
    void aFragmentOfAWordMatchesNothing() {
        published(1, "Space Action", "A pilot crosses the galaxy.", List.of("Drama"), 110);

        assertEquals(0, findText("act").total());
        assertTrue(findText("act").items().isEmpty());
    }

    @Test
    void everyWordOfTheTextIsRequiredInAnyOrder() {
        published(1, "Space Action", "A pilot crosses the galaxy.", List.of("Drama"), 110);
        published(2, "Galaxy Quest", "A crew in deep space.", List.of("Drama"), 150);
        published(3, "Quiet Garden", "A garden.", List.of("Drama"), 95);

        assertEquals(List.of(id(2), id(1)), ids(findText("space galaxy")));
        assertEquals(List.of(id(2), id(1)), ids(findText("galaxy   space")));
        assertEquals(0, findText("space garden").total());
    }

    @Test
    void aHyphenOrAQuoteInTheTextIsPartOfTheSearchNeverItsSyntax() {
        published(1, "Space Action", "A pilot crosses the galaxy.", List.of("Drama"), 110);
        published(2, "Quiet Garden", "A garden.", List.of("Drama"), 95);
        published(3, "Spider-Man", "A hero.", List.of("Drama"), 120);

        assertEquals(List.of(id(1)), ids(findText("-action")));
        assertEquals(0, findText("action\" OR \"garden").total());
        assertEquals(List.of(id(1)), ids(findText("\"action\"")));
        assertEquals(List.of(id(1)), ids(findText("action\\")));
        assertEquals(List.of(id(3)), ids(findText("spider-man")));
    }

    @Test
    void aTextWithNoSearchableWordMatchesNothing() {
        published(1, "Space Action", "A pilot crosses the galaxy.", List.of("Drama"), 110);

        MovieSearchPage page = findText("\"");

        assertEquals(0, page.total());
        assertTrue(page.items().isEmpty());
        assertEquals(1, page.page());
    }

    @Test
    void theGenreIsTheExactStoredValue() {
        published(1, "Space Action", "A pilot.", List.of("Ciencia ficción", "Acción"), 110);
        published(2, "Quiet Garden", "A garden.", List.of("Drama"), 95);
        published(3, "Galaxy Quest", "A crew.", List.of("Ciencia ficción"), 150);

        assertEquals(List.of(id(3), id(1)), ids(find(null, "Ciencia ficción", null, 1, 100)));
        assertEquals(0, find(null, "ciencia ficcion", null, 1, 100).total());
        assertEquals(0, find(null, "Ciencia", null, 1, 100).total());
    }

    @Test
    void theMaximumDurationIsInclusive() {
        published(1, "Short", "A short one.", List.of("Drama"), 90);
        published(2, "Exact", "An exact one.", List.of("Drama"), 120);
        published(3, "Long", "A long one.", List.of("Drama"), 121);

        assertEquals(List.of(id(2), id(1)), ids(find(null, null, 120, 1, 100)));
        assertEquals(0, find(null, null, 89, 1, 100).total());
    }

    @Test
    void theFiltersCombineWithAnd() {
        published(1, "Space Action", "A pilot.", List.of("Ciencia ficción"), 110);
        published(2, "Space Garden", "A garden in space.", List.of("Drama"), 100);
        published(3, "Space Epic", "An epic.", List.of("Ciencia ficción"), 150);

        assertEquals(List.of(id(1)), ids(find("space", "Ciencia ficción", 120, 1, 100)));
        assertEquals(List.of(id(1), id(3)), ids(find("space", "Ciencia ficción", null, 1, 100)));
    }

    @Test
    void aPageIsAWindowAndTheTotalCountsEveryMatch() {
        for (int n = 1; n <= 5; n++) {
            published(n, "Movie " + n, "A movie.", List.of("Drama"), 100);
        }

        MovieSearchPage second = find(null, null, null, 2, 2);

        assertEquals(List.of(id(3), id(4)), ids(second));
        assertEquals(5, second.total());
        assertEquals(2, second.page());
        assertEquals(2, second.limit());
        assertEquals(List.of(id(5)), ids(find(null, null, null, 3, 2)));
    }

    @Test
    void aPageBeyondTheLastIsEmptyAndKeepsTheTotal() {
        published(1, "Only One", "A movie.", List.of("Drama"), 100);

        MovieSearchPage page = find(null, null, null, 2, 20);

        assertTrue(page.items().isEmpty());
        assertEquals(1, page.total());
        assertEquals(2, page.page());
    }

    @Test
    void anEmptyStoreIsAnEmptyPage() {
        MovieSearchPage page = findAll();

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.total());
    }
}
