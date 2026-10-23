package co.edu.corhuila.csp.catalog.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The invariants and the lifecycle of the movie aggregate (HU-CATALOG-001, ADR-037). */
class MovieTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private static MovieDetails details() {
        return new MovieDetails("Synthetic Movie", null, 120, "PG-13", null, null, null, null);
    }

    private static Movie movieIn(PublicationStatus status) {
        return Movie.create(ID, details(), status == PublicationStatus.ARCHIVED ? PublicationStatus.DRAFT : status, NOW)
                .withStatus(status, NOW);
    }

    @Test
    void aValidMovieIsCreatedWithItsAuditDatesAndNoDeletion() {
        Movie movie = Movie.create(ID, details(), PublicationStatus.DRAFT, NOW);

        assertEquals(ID, movie.id());
        assertEquals(PublicationStatus.DRAFT, movie.status());
        assertEquals(NOW, movie.createdAt());
        assertEquals(NOW, movie.updatedAt());
        assertNull(movie.deletedAt());
    }

    @Test
    void aMovieCanBeCreatedAlreadyPublished() {
        assertEquals(PublicationStatus.PUBLISHED,
                Movie.create(ID, details(), PublicationStatus.PUBLISHED, NOW).status());
    }

    @Test
    void aMovieCannotBeCreatedArchived() {
        assertThrows(InvalidStatusTransitionException.class,
                () -> Movie.create(ID, details(), PublicationStatus.ARCHIVED, NOW));
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "-120"})
    void aDurationOfZeroOrLessIsRejected(int minutes) {
        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails("Synthetic Movie", null, minutes, "PG-13", null, null, null, null));
    }

    @Test
    void theTitleIsRequiredAndAtMost255Characters() {
        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails(null, null, 120, "PG-13", null, null, null, null));
        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails("  ", null, 120, "PG-13", null, null, null, null));
        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails("x".repeat(256), null, 120, "PG-13", null, null, null, null));
        assertEquals(255, new MovieDetails("x".repeat(255), null, 120, "PG-13", null, null, null, null).title().length());
    }

    @Test
    void theClassificationIsRequired() {
        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails("Synthetic Movie", null, 120, null, null, null, null, null));
        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails("Synthetic Movie", null, 120, " ", null, null, null, null));
    }

    @Test
    void atMost20GenresAndAtMost50CastMembersAreAccepted() {
        List<String> twentyOne = IntStream.rangeClosed(1, 21).mapToObj(i -> "Genre " + i).toList();
        List<String> fiftyOne = IntStream.rangeClosed(1, 51).mapToObj(i -> "Actor " + i).toList();

        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails("Synthetic Movie", null, 120, "PG-13", null, twentyOne, null, null));
        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails("Synthetic Movie", null, 120, "PG-13", null, null, fiftyOne, null));
        assertEquals(20, new MovieDetails("Synthetic Movie", null, 120, "PG-13", null,
                twentyOne.subList(0, 20), null, null).genres().size());
    }

    @Test
    void aGenreOrCastMemberCannotBeBlank() {
        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails("Synthetic Movie", null, 120, "PG-13", null, List.of("Drama", " "), null, null));
        assertThrows(InvalidMovieException.class,
                () -> new MovieDetails("Synthetic Movie", null, 120, "PG-13", null, null, List.of(""), null));
    }

    @Test
    void thePosterMustBeAnAbsoluteHttpAddress() {
        assertThrows(InvalidMovieException.class, () -> new MovieDetails("Synthetic Movie", null, 120, "PG-13", null,
                null, null, URI.create("poster.png")));
        assertThrows(InvalidMovieException.class, () -> new MovieDetails("Synthetic Movie", null, 120, "PG-13", null,
                null, null, URI.create("ftp://example.com/poster.png")));
        assertEquals(URI.create("https://example.com/poster.png"), new MovieDetails("Synthetic Movie", null, 120,
                "PG-13", LocalDate.of(2026, 12, 1), null, null, URI.create("https://example.com/poster.png")).posterUrl());
    }

    @Test
    void aDraftIsPublishedAndTheModificationDateMoves() {
        Movie published = Movie.create(ID, details(), PublicationStatus.DRAFT, NOW)
                .withStatus(PublicationStatus.PUBLISHED, LATER);

        assertEquals(PublicationStatus.PUBLISHED, published.status());
        assertEquals(NOW, published.createdAt());
        assertEquals(LATER, published.updatedAt());
        assertNull(published.deletedAt());
    }

    @ParameterizedTest
    @CsvSource({
        "DRAFT,PUBLISHED,true", "DRAFT,ARCHIVED,true", "PUBLISHED,ARCHIVED,true",
        "PUBLISHED,DRAFT,false", "ARCHIVED,DRAFT,false", "ARCHIVED,PUBLISHED,false"})
    void onlyTheTransitionsOfAdr037AreAllowed(PublicationStatus from, PublicationStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransitionTo(to));
    }

    @Test
    void aForbiddenTransitionIsRejectedAndChangesNothing() {
        Movie published = movieIn(PublicationStatus.PUBLISHED);

        assertThrows(InvalidStatusTransitionException.class, () -> published.withStatus(PublicationStatus.DRAFT, LATER));
        assertEquals(PublicationStatus.PUBLISHED, published.status());
        assertEquals(NOW, published.updatedAt());
    }

    @Test
    void settingTheCurrentStatusAgainChangesNothing() {
        Movie published = movieIn(PublicationStatus.PUBLISHED);

        assertSame(published, published.withStatus(PublicationStatus.PUBLISHED, LATER));
    }

    @Test
    void archivingSetsTheStatusAndTheDeletionDateAndNeverRemovesTheMovie() {
        Movie archived = movieIn(PublicationStatus.PUBLISHED).archive(LATER);

        assertEquals(PublicationStatus.ARCHIVED, archived.status());
        assertEquals(LATER, archived.deletedAt());
        assertEquals(LATER, archived.updatedAt());
        assertEquals(ID, archived.id());
        assertTrue(archived.isArchived());
        assertFalse(movieIn(PublicationStatus.DRAFT).isArchived());
    }

    @Test
    void anArchivedMovieIsFinalAndReadOnly() {
        Movie archived = movieIn(PublicationStatus.DRAFT).archive(NOW);

        assertThrows(InvalidStatusTransitionException.class, () -> archived.archive(LATER));
        assertThrows(InvalidStatusTransitionException.class, () -> archived.withStatus(PublicationStatus.PUBLISHED, LATER));
        assertThrows(InvalidStatusTransitionException.class,
                () -> archived.change(new MovieChanges("New title", null, null, null, null, null, null, null, null), LATER));
    }

    @Test
    void aPartialChangeKeepsWhatIsNotSentAndMovesTheModificationDate() {
        Movie movie = Movie.create(ID, details(), PublicationStatus.DRAFT, NOW);

        Movie changed = movie.change(new MovieChanges("New title", "A synopsis", null, null, null, null, null, null,
                PublicationStatus.PUBLISHED), LATER);

        assertEquals("New title", changed.details().title());
        assertEquals("A synopsis", changed.details().synopsis());
        assertEquals(120, changed.details().durationMinutes());
        assertEquals("PG-13", changed.details().classification());
        assertEquals(PublicationStatus.PUBLISHED, changed.status());
        assertEquals(LATER, changed.updatedAt());
    }

    @Test
    void aChangeThatBreaksAnInvariantIsRejectedAndTheMovieIsUntouched() {
        Movie movie = Movie.create(ID, details(), PublicationStatus.DRAFT, NOW);

        assertThrows(InvalidMovieException.class,
                () -> movie.change(new MovieChanges(null, null, 0, null, null, null, null, null, null), LATER));
        assertEquals(120, movie.details().durationMinutes());
        assertEquals(NOW, movie.updatedAt());
    }

    @Test
    void aMovieBuiltWithoutItsIdentityOrDatesIsMalformed() {
        assertThrows(InvalidMovieException.class, () -> Movie.create(null, details(), PublicationStatus.DRAFT, NOW));
        assertThrows(InvalidMovieException.class, () -> Movie.create(ID, details(), PublicationStatus.DRAFT, null));
        assertThrows(InvalidMovieException.class, () -> Movie.create(ID, null, PublicationStatus.DRAFT, NOW));
        assertThrows(InvalidMovieException.class, () -> Movie.create(ID, details(), null, NOW));
    }
}
