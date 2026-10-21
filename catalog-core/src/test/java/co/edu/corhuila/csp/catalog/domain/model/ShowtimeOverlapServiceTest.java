package co.edu.corhuila.csp.catalog.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** No two scheduled showtimes of a room overlap (HU-CATALOG-003, ADR-039). */
class ShowtimeOverlapServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER_ROOM_ID = UUID.fromString("22222222-2222-4222-8222-222222222223");
    private static final UUID CANDIDATE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID EXISTING_ID = UUID.fromString("33333333-3333-4333-8333-333333333334");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private static Showtime showtime(UUID id, UUID roomId, String start, String end, ShowtimeStatus status) {
        TimeInterval interval = new TimeInterval(Instant.parse("2026-11-01T" + start + ":00Z"),
                Instant.parse("2026-11-01T" + end + ":00Z"));
        return new Showtime(id, UUID.randomUUID(), roomId, "Synthetic Movie", "Room 1", interval, 100L, null, null,
                status, NOW, NOW);
    }

    private static Showtime candidate() {
        return showtime(CANDIDATE_ID, ROOM_ID, "21:00", "23:00", ShowtimeStatus.SCHEDULED);
    }

    @Test
    void aScheduledShowtimeThatOverlapsIsRejectedAndNamesTheConflict() {
        Showtime existing = showtime(EXISTING_ID, ROOM_ID, "20:00", "22:00", ShowtimeStatus.SCHEDULED);

        ShowtimeOverlapException error = assertThrows(ShowtimeOverlapException.class,
                () -> ShowtimeOverlapService.requireNoOverlap(candidate(), List.of(existing)));

        assertTrue(error.getMessage().contains(EXISTING_ID.toString()));
    }

    @Test
    void aCancelledShowtimeIsIgnored() {
        Showtime existing = showtime(EXISTING_ID, ROOM_ID, "20:00", "22:00", ShowtimeStatus.CANCELLED);

        assertDoesNotThrow(() -> ShowtimeOverlapService.requireNoOverlap(candidate(), List.of(existing)));
    }

    @Test
    void aCompletedShowtimeIsIgnored() {
        Showtime existing = showtime(EXISTING_ID, ROOM_ID, "20:00", "22:00", ShowtimeStatus.COMPLETED);

        assertDoesNotThrow(() -> ShowtimeOverlapService.requireNoOverlap(candidate(), List.of(existing)));
    }

    @Test
    void theCandidateItselfIsIgnored() {
        Showtime itself = showtime(CANDIDATE_ID, ROOM_ID, "20:00", "22:00", ShowtimeStatus.SCHEDULED);

        assertDoesNotThrow(() -> ShowtimeOverlapService.requireNoOverlap(candidate(), List.of(itself)));
    }

    @Test
    void anAdjacentShowtimeIsNotAnOverlap() {
        Showtime before = showtime(EXISTING_ID, ROOM_ID, "19:00", "21:00", ShowtimeStatus.SCHEDULED);
        Showtime after = showtime(UUID.randomUUID(), ROOM_ID, "23:00", "23:59", ShowtimeStatus.SCHEDULED);

        assertDoesNotThrow(() -> ShowtimeOverlapService.requireNoOverlap(candidate(), List.of(before, after)));
    }

    @Test
    void aShowtimeOfAnotherRoomIsIgnored() {
        Showtime elsewhere = showtime(EXISTING_ID, OTHER_ROOM_ID, "20:00", "22:00", ShowtimeStatus.SCHEDULED);

        assertDoesNotThrow(() -> ShowtimeOverlapService.requireNoOverlap(candidate(), List.of(elsewhere)));
    }

    @Test
    void anEmptyRoomHasNoConflict() {
        assertDoesNotThrow(() -> ShowtimeOverlapService.requireNoOverlap(candidate(), List.of()));
    }
}
