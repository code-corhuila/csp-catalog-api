package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** The document the validator of catalog_db.movies expects, and the way back (ADR-016, ADR-037). */
class MovieDocumentMapperTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");

    private static Movie full() {
        return Movie.create(ID, new MovieDetails("Synthetic Movie", "A synopsis", 120, "PG-13",
                LocalDate.of(2026, 12, 1), List.of("Drama", "Comedy"), List.of("Synthetic Actor"),
                URI.create("https://example.com/poster.png")), PublicationStatus.PUBLISHED, NOW);
    }

    private static Movie minimal() {
        return Movie.create(ID, new MovieDetails("Synthetic Movie", null, 90, "G", null, null, null, null),
                PublicationStatus.DRAFT, NOW);
    }

    @Test
    void theDocumentUsesTheFieldNamesAndTypesOfTheValidator() {
        Document document = MovieDocumentMapper.toDocument(full());

        assertEquals(Set.of("_id", "title", "synopsis", "duration_minutes", "classification", "release_date",
                "genres", "cast", "poster_url", "publication_status", "created_at", "updated_at", "deleted_at"),
                document.keySet());
        assertEquals(ID.toString(), document.get("_id"));
        assertInstanceOf(Integer.class, document.get("duration_minutes"));
        assertEquals("PUBLISHED", document.get("publication_status"));
        assertEquals("https://example.com/poster.png", document.get("poster_url"));
        assertEquals(Date.from(NOW), document.get("created_at"));
        assertEquals(Date.from(NOW), document.get("updated_at"));
        assertNull(document.get("deleted_at"));
        assertEquals(List.of("Drama", "Comedy"), document.get("genres"));
        assertEquals(List.of("Synthetic Actor"), document.get("cast"));
    }

    @Test
    void theReleaseDateIsStoredAsTheStartOfTheDayInUtc() {
        Document document = MovieDocumentMapper.toDocument(full());

        assertEquals(Date.from(Instant.parse("2026-12-01T00:00:00Z")), document.get("release_date"));
    }

    @Test
    void aFullMovieSurvivesTheRoundTrip() {
        assertEquals(full(), MovieDocumentMapper.toMovie(MovieDocumentMapper.toDocument(full())));
    }

    @Test
    void aMovieWithoutOptionalFieldsSurvivesTheRoundTripWithNullsInTheDocument() {
        Document document = MovieDocumentMapper.toDocument(minimal());

        assertTrue(document.containsKey("synopsis") && document.get("synopsis") == null);
        assertNull(document.get("release_date"));
        assertNull(document.get("poster_url"));
        assertEquals(minimal(), MovieDocumentMapper.toMovie(document));
    }

    @Test
    void anArchivedMovieKeepsItsDeletionDate() {
        Movie archived = minimal().archive(NOW.plusSeconds(60));

        Document document = MovieDocumentMapper.toDocument(archived);

        assertEquals("ARCHIVED", document.get("publication_status"));
        assertEquals(Date.from(NOW.plusSeconds(60)), document.get("deleted_at"));
        assertEquals(archived, MovieDocumentMapper.toMovie(document));
    }

    @Test
    void aDocumentWithoutGenresOrCastBecomesAMovieWithEmptyLists() {
        Document document = MovieDocumentMapper.toDocument(minimal());
        document.put("genres", null);
        document.remove("cast");

        Movie movie = MovieDocumentMapper.toMovie(document);

        assertEquals(List.of(), movie.details().genres());
        assertEquals(List.of(), movie.details().cast());
    }
}
