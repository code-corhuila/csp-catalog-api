package co.edu.corhuila.csp.catalog.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The invariants and the lifecycle of the showtime aggregate (HU-CATALOG-003, ADR-039). */
class ShowtimeTest {

    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MOVIE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ROOM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final Instant LATER = NOW.plusSeconds(60);
    private static final Instant START = Instant.parse("2026-11-01T20:00:00Z");
    private static final Instant END = Instant.parse("2026-11-01T22:00:00Z");
    private static final TimeInterval INTERVAL = new TimeInterval(START, END);

    private static Movie movieIn(PublicationStatus status) {
        MovieDetails details = new MovieDetails("Synthetic Movie", null, 120, "PG-13", null, null, null, null);
        return Movie.create(MOVIE_ID, details,
                status == PublicationStatus.ARCHIVED ? PublicationStatus.DRAFT : status, NOW).withStatus(status, NOW);
    }

    private static Room room() {
        RoomDetails details = new RoomDetails("Room 1", List.of(new Seat("A1", "A", 1, SeatType.STANDARD)));
        return Room.create(ROOM_ID, details, NOW);
    }

    private static Showtime scheduled() {
        return Showtime.schedule(ID, movieIn(PublicationStatus.PUBLISHED), room(), INTERVAL, 15000L, "en", "es", NOW);
    }

    private static Showtime in(ShowtimeStatus status) {
        return new Showtime(ID, MOVIE_ID, ROOM_ID, "Synthetic Movie", "Room 1", INTERVAL, 15000L, null, null,
                status, NOW, NOW);
    }

    private static ShowtimeChanges changes(Instant startsAt, Instant endsAt, Long price, ShowtimeStatus status) {
        return new ShowtimeChanges(startsAt, endsAt, price, null, null, status);
    }

    @Test
    void schedulingCopiesTheSnapshotsAndTheReferencesAndStartsScheduled() {
        Showtime showtime = scheduled();

        assertEquals(ID, showtime.id());
        assertEquals(MOVIE_ID, showtime.movieId());
        assertEquals(ROOM_ID, showtime.roomId());
        assertEquals("Synthetic Movie", showtime.movieTitleSnapshot());
        assertEquals("Room 1", showtime.roomNameSnapshot());
        assertEquals(INTERVAL, showtime.interval());
        assertEquals(15000L, showtime.priceCents());
        assertEquals("en", showtime.audioLanguage());
        assertEquals("es", showtime.subtitleLanguage());
        assertEquals(ShowtimeStatus.SCHEDULED, showtime.status());
        assertEquals(NOW, showtime.createdAt());
        assertEquals(NOW, showtime.updatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "ARCHIVED"})
    void aMovieThatIsNotPublishedCannotBeScheduled(PublicationStatus status) {
        ShowtimeReferenceException error = assertThrows(ShowtimeReferenceException.class,
                () -> Showtime.schedule(ID, movieIn(status), room(), INTERVAL, 100L, null, null, NOW));

        assertTrue(error.getMessage().contains("movie"));
    }

    @Test
    void anArchivedRoomCannotBeScheduled() {
        Room archived = room().archive(LATER);

        ShowtimeReferenceException error = assertThrows(ShowtimeReferenceException.class,
                () -> Showtime.schedule(ID, movieIn(PublicationStatus.PUBLISHED), archived, INTERVAL, 100L, null,
                        null, NOW));

        assertTrue(error.getMessage().contains("room"));
    }

    @Test
    void aNegativePriceIsRejectedAndZeroIsAccepted() {
        assertThrows(InvalidShowtimeException.class,
                () -> Showtime.schedule(ID, movieIn(PublicationStatus.PUBLISHED), room(), INTERVAL, -1L, null, null,
                        NOW));
        assertEquals(0L, Showtime.schedule(ID, movieIn(PublicationStatus.PUBLISHED), room(), INTERVAL, 0L, null,
                null, NOW).priceCents());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void aBlankLanguageIsRejected(String language) {
        assertThrows(InvalidShowtimeException.class,
                () -> Showtime.schedule(ID, movieIn(PublicationStatus.PUBLISHED), room(), INTERVAL, 0L, language,
                        null, NOW));
        assertThrows(InvalidShowtimeException.class,
                () -> Showtime.schedule(ID, movieIn(PublicationStatus.PUBLISHED), room(), INTERVAL, 0L, null,
                        language, NOW));
    }

    @Test
    void theLanguagesAreOptional() {
        Showtime showtime = in(ShowtimeStatus.SCHEDULED);

        assertNull(showtime.audioLanguage());
        assertNull(showtime.subtitleLanguage());
    }

    @Test
    void aMalformedShowtimeIsRejected() {
        assertThrows(InvalidShowtimeException.class, () -> new Showtime(null, MOVIE_ID, ROOM_ID, "M", "R", INTERVAL,
                0L, null, null, ShowtimeStatus.SCHEDULED, NOW, NOW));
        assertThrows(InvalidShowtimeException.class, () -> new Showtime(ID, null, ROOM_ID, "M", "R", INTERVAL,
                0L, null, null, ShowtimeStatus.SCHEDULED, NOW, NOW));
        assertThrows(InvalidShowtimeException.class, () -> new Showtime(ID, MOVIE_ID, null, "M", "R", INTERVAL,
                0L, null, null, ShowtimeStatus.SCHEDULED, NOW, NOW));
        assertThrows(InvalidShowtimeException.class, () -> new Showtime(ID, MOVIE_ID, ROOM_ID, " ", "R", INTERVAL,
                0L, null, null, ShowtimeStatus.SCHEDULED, NOW, NOW));
        assertThrows(InvalidShowtimeException.class, () -> new Showtime(ID, MOVIE_ID, ROOM_ID, "M", null, INTERVAL,
                0L, null, null, ShowtimeStatus.SCHEDULED, NOW, NOW));
        assertThrows(InvalidShowtimeException.class, () -> new Showtime(ID, MOVIE_ID, ROOM_ID, "M", "R", null,
                0L, null, null, ShowtimeStatus.SCHEDULED, NOW, NOW));
        assertThrows(InvalidShowtimeException.class, () -> new Showtime(ID, MOVIE_ID, ROOM_ID, "M", "R", INTERVAL,
                0L, null, null, null, NOW, NOW));
        assertThrows(InvalidShowtimeException.class, () -> new Showtime(ID, MOVIE_ID, ROOM_ID, "M", "R", INTERVAL,
                0L, null, null, ShowtimeStatus.SCHEDULED, null, NOW));
        assertThrows(InvalidShowtimeException.class, () -> new Showtime(ID, MOVIE_ID, ROOM_ID, "M", "R", INTERVAL,
                0L, null, null, ShowtimeStatus.SCHEDULED, NOW, null));
    }

    @ParameterizedTest(name = "{0} to {1} is allowed: {2}")
    @CsvSource({
            "SCHEDULED, CANCELLED, true",
            "SCHEDULED, COMPLETED, true",
            "SCHEDULED, SCHEDULED, false",
            "CANCELLED, SCHEDULED, false",
            "CANCELLED, COMPLETED, false",
            "CANCELLED, CANCELLED, false",
            "COMPLETED, SCHEDULED, false",
            "COMPLETED, CANCELLED, false",
            "COMPLETED, COMPLETED, false"
    })
    void theStatusTransitionsFollowTheLifecycle(ShowtimeStatus from, ShowtimeStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransitionTo(to));
    }

    @Test
    void changingOnlyTheEndKeepsTheStart() {
        Instant newEnd = END.plusSeconds(1800);

        Showtime changed = scheduled().change(changes(null, newEnd, null, null), LATER);

        assertEquals(new TimeInterval(START, newEnd), changed.interval());
    }

    @Test
    void changingOnlyTheStartKeepsTheEnd() {
        Instant newStart = START.minusSeconds(1800);

        assertEquals(new TimeInterval(newStart, END),
                scheduled().change(changes(newStart, null, null, null), LATER).interval());
    }

    @Test
    void whatIsNotSentIsKeptAndTheUpdateDateMoves() {
        Showtime original = scheduled();

        Showtime changed = original.change(changes(null, null, 9000L, null), LATER);

        assertEquals(9000L, changed.priceCents());
        assertEquals(INTERVAL, changed.interval());
        assertEquals("en", changed.audioLanguage());
        assertEquals("es", changed.subtitleLanguage());
        assertEquals(ShowtimeStatus.SCHEDULED, changed.status());
        assertEquals(NOW, changed.createdAt());
        assertEquals(LATER, changed.updatedAt());
    }

    @Test
    void theLanguagesCanBeChanged() {
        Showtime changed = scheduled().change(new ShowtimeChanges(null, null, null, "fr", "de", null), LATER);

        assertEquals("fr", changed.audioLanguage());
        assertEquals("de", changed.subtitleLanguage());
    }

    @Test
    void anEndBeforeTheStartIsRejectedAndTheOriginalIsUntouched() {
        Showtime original = scheduled();

        assertThrows(InvalidShowtimeException.class,
                () -> original.change(changes(null, START.minusSeconds(1), null, null), LATER));

        assertEquals(INTERVAL, original.interval());
        assertEquals(NOW, original.updatedAt());
    }

    @Test
    void aNegativePriceInAChangeIsRejected() {
        assertThrows(InvalidShowtimeException.class, () -> scheduled().change(changes(null, null, -5L, null), LATER));
    }

    @Test
    void aChangeCanCompleteAScheduledShowtime() {
        assertEquals(ShowtimeStatus.COMPLETED,
                scheduled().change(changes(null, null, null, ShowtimeStatus.COMPLETED), LATER).status());
    }

    @Test
    void aChangeCanCancelAScheduledShowtime() {
        assertEquals(ShowtimeStatus.CANCELLED,
                scheduled().change(changes(null, null, null, ShowtimeStatus.CANCELLED), LATER).status());
    }

    @Test
    void askingForTheCurrentStatusChangesNothing() {
        Showtime changed = scheduled().change(changes(null, null, null, ShowtimeStatus.SCHEDULED), LATER);

        assertEquals(ShowtimeStatus.SCHEDULED, changed.status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "COMPLETED"})
    void aShowtimeThatIsNotScheduledCannotBeChanged(ShowtimeStatus status) {
        Showtime showtime = in(status);

        assertThrows(InvalidStatusTransitionException.class,
                () -> showtime.change(changes(null, null, 1L, null), LATER));
    }

    @Test
    void cancellingMovesAScheduledShowtimeToCancelled() {
        Showtime cancelled = scheduled().cancel(LATER);

        assertEquals(ShowtimeStatus.CANCELLED, cancelled.status());
        assertEquals(LATER, cancelled.updatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "COMPLETED"})
    void aShowtimeThatIsNotScheduledCannotBeCancelled(ShowtimeStatus status) {
        Showtime showtime = in(status);

        assertThrows(InvalidStatusTransitionException.class, () -> showtime.cancel(LATER));
    }

    @Test
    void theShowtimeIsOnlyActiveWhileScheduled() {
        assertTrue(in(ShowtimeStatus.SCHEDULED).isScheduled());
        assertFalse(in(ShowtimeStatus.CANCELLED).isScheduled());
        assertFalse(in(ShowtimeStatus.COMPLETED).isScheduled());
    }

    @Test
    void thePublishedEventCarriesTheSnapshotsTheIntervalThePriceAndTheLanguages() {
        Showtime showtime = scheduled();

        ShowtimePublished event = showtime.publishedEvent();

        assertEquals(new ShowtimePublished(ID, MOVIE_ID, ROOM_ID, "Synthetic Movie", "Room 1", START, END, 15000L,
                "en", "es", NOW), event);
        assertSame(showtime.createdAt(), event.occurredAt());
    }
}
