package co.edu.corhuila.csp.catalog.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The invariants of the room aggregate and of its seat layout (HU-CATALOG-002). */
class RoomTest {

    private static final UUID ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private static Seat seat(String label) {
        return new Seat(label, "A", 1, SeatType.STANDARD);
    }

    private static List<Seat> seats(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(n -> seat("S" + n)).toList();
    }

    private static RoomDetails details() {
        return new RoomDetails("Room 1", List.of(seat("A1"), seat("A2")));
    }

    private static Room room() {
        return Room.create(ID, details(), NOW);
    }

    @Test
    void aValidRoomIsCreatedWithItsAuditDatesAndNoDeletion() {
        Room room = room();

        assertEquals(ID, room.id());
        assertEquals(NOW, room.createdAt());
        assertEquals(NOW, room.updatedAt());
        assertNull(room.deletedAt());
        assertFalse(room.isArchived());
    }

    @Test
    void theCapacityIsTheNumberOfSeats() {
        assertEquals(2, details().capacity());
        assertEquals(37, new RoomDetails("Big", seats(37)).capacity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void aBlankNameIsRejected(String name) {
        assertThrows(InvalidRoomException.class, () -> new RoomDetails(name, seats(1)));
    }

    @Test
    void aNullNameIsRejected() {
        assertThrows(InvalidRoomException.class, () -> new RoomDetails(null, seats(1)));
    }

    @Test
    void aNameOfMoreThan100CharactersIsRejectedAndOneOf100IsAccepted() {
        assertThrows(InvalidRoomException.class, () -> new RoomDetails("x".repeat(101), seats(1)));
        assertEquals(100, new RoomDetails("x".repeat(100), seats(1)).name().length());
    }

    @Test
    void aRoomWithoutSeatsIsRejected() {
        assertThrows(InvalidRoomException.class, () -> new RoomDetails("Room", List.of()));
        assertThrows(InvalidRoomException.class, () -> new RoomDetails("Room", null));
    }

    @Test
    void atMost500SeatsAreAccepted() {
        assertEquals(500, new RoomDetails("Room", seats(500)).capacity());
        assertThrows(InvalidRoomException.class, () -> new RoomDetails("Room", seats(501)));
    }

    @Test
    void aRepeatedLabelIsRejectedAndTheMessageNamesIt() {
        InvalidRoomException error = assertThrows(InvalidRoomException.class,
                () -> new RoomDetails("Room", List.of(seat("A1"), seat("B1"), seat("A1"))));

        assertTrue(error.getMessage().contains("A1"));
    }

    @Test
    void distinctLabelsAreAccepted() {
        assertEquals(3, new RoomDetails("Room", List.of(seat("A1"), seat("a1"), seat("A2"))).capacity());
    }

    @Test
    void theSeatsListCannotBeModifiedFromOutside() {
        RoomDetails details = details();

        assertThrows(UnsupportedOperationException.class, () -> details.seats().add(seat("Z9")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void aBlankLabelIsRejected(String label) {
        assertThrows(InvalidRoomException.class, () -> new Seat(label, "A", 1, SeatType.STANDARD));
    }

    @Test
    void aNullLabelIsRejected() {
        assertThrows(InvalidRoomException.class, () -> new Seat(null, "A", 1, SeatType.STANDARD));
    }

    @Test
    void aBlankRowIsRejectedAndAMissingRowIsAccepted() {
        assertThrows(InvalidRoomException.class, () -> new Seat("A1", " ", 1, SeatType.STANDARD));
        assertNull(new Seat("A1", null, 1, SeatType.STANDARD).row());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void aNumberBelowOneIsRejected(int number) {
        assertThrows(InvalidRoomException.class, () -> new Seat("A1", "A", number, SeatType.STANDARD));
    }

    @Test
    void aMissingNumberIsAccepted() {
        assertNull(new Seat("A1", "A", null, SeatType.STANDARD).number());
    }

    @Test
    void aSeatWithoutTypeIsStandard() {
        assertEquals(SeatType.STANDARD, new Seat("A1", "A", 1, null).type());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STANDARD", "VIP", "ACCESSIBLE"})
    void everySeatTypeIsKept(String type) {
        SeatType seatType = SeatType.valueOf(type);

        assertEquals(seatType, new Seat("A1", "A", 1, seatType).type());
    }

    @Test
    void archivingSetsTheDeletionAndUpdateDatesAndKeepsTheRoom() {
        Room archived = room().archive(LATER);

        assertTrue(archived.isArchived());
        assertEquals(LATER, archived.deletedAt());
        assertEquals(LATER, archived.updatedAt());
        assertEquals(NOW, archived.createdAt());
        assertEquals(ID, archived.id());
        assertEquals(details(), archived.details());
    }

    @Test
    void anArchivedRoomCannotBeArchivedAgain() {
        Room archived = room().archive(LATER);

        assertThrows(RoomNotFoundException.class, () -> archived.archive(LATER.plusSeconds(1)));
    }

    @Test
    void anArchivedRoomCannotBeChanged() {
        Room archived = room().archive(LATER);

        assertThrows(RoomNotFoundException.class,
                () -> archived.change(new RoomChanges("New name", null), LATER.plusSeconds(1)));
    }

    @Test
    void theNotFoundMessageNamesTheRoom() {
        assertEquals("Room " + ID + " was not found", new RoomNotFoundException(ID).getMessage());
    }

    @Test
    void aPartialChangeKeepsWhatWasNotSentAndMovesTheUpdateDate() {
        Room changed = room().change(new RoomChanges("Renamed", null), LATER);

        assertEquals("Renamed", changed.details().name());
        assertEquals(details().seats(), changed.details().seats());
        assertEquals(LATER, changed.updatedAt());
        assertEquals(NOW, changed.createdAt());
    }

    @Test
    void aChangeOfTheSeatsKeepsTheName() {
        Room changed = room().change(new RoomChanges(null, List.of(seat("Z1"))), LATER);

        assertEquals("Room 1", changed.details().name());
        assertEquals(1, changed.details().capacity());
    }

    @Test
    void aChangeThatDuplicatesALabelIsRejectedAndTheRoomIsUntouched() {
        Room room = room();

        assertThrows(InvalidRoomException.class,
                () -> room.change(new RoomChanges(null, List.of(seat("A1"), seat("A1"))), LATER));
        assertEquals(details(), room.details());
        assertEquals(NOW, room.updatedAt());
    }

    @Test
    void aChangeThatEmptiesTheSeatsIsRejected() {
        assertThrows(InvalidRoomException.class, () -> room().change(new RoomChanges(null, List.of()), LATER));
    }

    @Test
    void aChangeWithAnInvalidNameIsRejected() {
        assertThrows(InvalidRoomException.class, () -> room().change(new RoomChanges(" ", null), LATER));
    }

    @Test
    void aMalformedRoomIsRejected() {
        assertThrows(InvalidRoomException.class, () -> new Room(null, details(), NOW, NOW, null));
        assertThrows(InvalidRoomException.class, () -> new Room(ID, null, NOW, NOW, null));
        assertThrows(InvalidRoomException.class, () -> new Room(ID, details(), null, NOW, null));
        assertThrows(InvalidRoomException.class, () -> new Room(ID, details(), NOW, null, null));
    }
}
