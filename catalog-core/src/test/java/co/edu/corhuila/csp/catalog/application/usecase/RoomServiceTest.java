package co.edu.corhuila.csp.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.csp.catalog.application.port.in.ArchiveRoomInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateRoomInput;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateRoomInput;
import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.RoomNameAlreadyInUseException;
import co.edu.corhuila.csp.catalog.application.port.out.RoomPage;
import co.edu.corhuila.csp.catalog.application.port.out.RoomRepository;
import co.edu.corhuila.csp.catalog.application.port.out.RoomWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.InvalidRoomException;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomChanges;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.RoomNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
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

/** The use cases of the room writes and reads, with a fake of the output port (Annex C). */
class RoomServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123456789Z");
    private static final Instant NOW_IN_MILLIS = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final UUID ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String KEY = "synthetic-key-0001";

    private final FakeRooms rooms = new FakeRooms();
    private final RoomService service = new RoomService(rooms, () -> ID, Clock.fixed(NOW, ZoneOffset.UTC));

    private static List<Seat> seats() {
        return List.of(new Seat("A1", "A", 1, null), new Seat("A2", "A", 2, null));
    }

    private static RoomDetails details() {
        return new RoomDetails("Synthetic Room", seats());
    }

    private static CreateRoomInput create(String key) {
        return new CreateRoomInput(key, details(), null);
    }

    private static RoomChanges nameChange(String name) {
        return new RoomChanges(name, null);
    }

    @Test
    void aCreatedRoomTakesItsIdAndDatesFromThePortsAtTheMillisecondTheDatabaseKeeps() {
        Room room = service.createRoom(create(KEY));

        assertEquals(ID, room.id());
        assertEquals(NOW_IN_MILLIS, room.createdAt());
        assertEquals(NOW_IN_MILLIS, room.updatedAt());
        assertNull(room.deletedAt());
        assertEquals(room, rooms.stored.get(ID));
    }

    @Test
    void theWriteCarriesItsKeyItsOperationAndAHashOfTheRequest() {
        service.createRoom(create(KEY));

        RoomWrite write = rooms.writes.get(0);
        assertEquals(KEY, write.idempotencyKey());
        assertEquals(WriteOperation.CREATE_ROOM, write.operation());
        assertEquals(64, write.requestHash().length());
        assertTrue(write.requestHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void theSameRequestHasTheSameHashAndAnotherRequestHasAnother() {
        service.createRoom(create("synthetic-key-0001"));
        service.createRoom(create("synthetic-key-0002"));
        service.createRoom(new CreateRoomInput("synthetic-key-0003",
                new RoomDetails("Another Room", seats()), null));

        assertEquals(rooms.writes.get(0).requestHash(), rooms.writes.get(1).requestHash());
        assertNotEquals(rooms.writes.get(0).requestHash(), rooms.writes.get(2).requestHash());
    }

    @Test
    void aReplayedCreationReturnsTheStoredRoomAndWritesNothingMore() {
        Room first = service.createRoom(create(KEY));

        Room replay = service.createRoom(create(KEY));

        assertEquals(first, replay);
        assertEquals(1, rooms.writes.size());
    }

    @Test
    void theSameKeyWithAnotherRequestIsAConflict() {
        service.createRoom(create(KEY));

        assertThrows(IdempotencyKeyConflictException.class, () -> service.createRoom(
                new CreateRoomInput(KEY, new RoomDetails("Another Room", seats()), null)));
    }

    @Test
    void aBrokenInvariantStopsTheCreationBeforeAnythingIsWritten() {
        assertThrows(InvalidRoomException.class, () -> service.createRoom(
                new CreateRoomInput(KEY, new RoomDetails(" ", seats()), null)));
        assertTrue(rooms.writes.isEmpty());
    }

    @Test
    void aDeclaredCapacityThatDiffersFromTheSeatsIsRejectedBeforeAnythingIsWritten() {
        InvalidRoomException error = assertThrows(InvalidRoomException.class,
                () -> service.createRoom(new CreateRoomInput(KEY, details(), 3)));

        assertEquals("the capacity must equal the number of seats", error.getMessage());
        assertTrue(rooms.writes.isEmpty());
    }

    @Test
    void aDeclaredCapacityThatEqualsTheSeatsIsAccepted() {
        Room room = service.createRoom(new CreateRoomInput(KEY, details(), 2));

        assertEquals(2, room.details().capacity());
        assertEquals(1, rooms.writes.size());
    }

    @Test
    void aNameAlreadyInUseIsDecidedByTheDatabaseAndPropagates() {
        rooms.nameInUse = true;

        assertThrows(RoomNameAlreadyInUseException.class, () -> service.createRoom(create(KEY)));
    }

    @Test
    void aRoomIsReadById() {
        service.createRoom(create(KEY));

        assertEquals(ID, service.getRoom(ID).id());
    }

    @Test
    void anUnknownRoomIsNotFound() {
        assertThrows(RoomNotFoundException.class, () -> service.getRoom(ID));
    }

    @Test
    void anArchivedRoomIsNotFound() {
        service.createRoom(create(KEY));
        service.archiveRoom(new ArchiveRoomInput("synthetic-key-0002", ID));

        assertThrows(RoomNotFoundException.class, () -> service.getRoom(ID));
    }

    @Test
    void theListHoldsTheActiveRoomsOfTheWindowAndReportsTheTotal() {
        service.createRoom(create(KEY));

        RoomPage page = service.listRooms(2, 10);

        assertEquals(2, page.page());
        assertEquals(10, page.limit());
        assertEquals(1, page.total());
        assertEquals(1, page.items().size());
    }

    @Test
    void anUpdateAppliesTheChangesAndMovesTheModificationDate() {
        service.createRoom(create(KEY));
        RoomService later = new RoomService(rooms, () -> ID, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));

        Room updated = later.updateRoom(new UpdateRoomInput("synthetic-key-0002", ID, nameChange("New name")));

        assertEquals("New name", updated.details().name());
        assertEquals(NOW_IN_MILLIS, updated.createdAt());
        assertEquals(NOW_IN_MILLIS.plusSeconds(60), updated.updatedAt());
        assertEquals(WriteOperation.UPDATE_ROOM, rooms.writes.get(1).operation());
    }

    @Test
    void anUpdateOfAnUnknownRoomIsNotFound() {
        assertThrows(RoomNotFoundException.class,
                () -> service.updateRoom(new UpdateRoomInput(KEY, ID, nameChange("New name"))));
    }

    @Test
    void aReplayedUpdateAnswersBeforeTheRulesOfTheStateAreEvaluated() {
        service.createRoom(create(KEY));
        UpdateRoomInput rename = new UpdateRoomInput("synthetic-key-0002", ID, nameChange("New name"));
        Room updated = service.updateRoom(rename);

        Room replay = service.updateRoom(rename);

        assertEquals(updated, replay);
        assertEquals(2, rooms.writes.size());
    }

    @Test
    void anUpdateAfterArchiveIsNotFound() {
        service.createRoom(create(KEY));
        service.archiveRoom(new ArchiveRoomInput("synthetic-key-0002", ID));

        assertThrows(RoomNotFoundException.class,
                () -> service.updateRoom(new UpdateRoomInput("synthetic-key-0003", ID, nameChange("New name"))));
    }

    @Test
    void aReplayedUpdateAfterArchiveIsNotFound() {
        service.createRoom(create(KEY));
        UpdateRoomInput rename = new UpdateRoomInput("synthetic-key-0002", ID, nameChange("New name"));
        service.updateRoom(rename);
        service.archiveRoom(new ArchiveRoomInput("synthetic-key-0003", ID));

        assertThrows(RoomNotFoundException.class, () -> service.updateRoom(rename));
        assertEquals(3, rooms.writes.size());
    }

    @Test
    void archivingSetsTheDeletionDateAndKeepsTheRoom() {
        service.createRoom(create(KEY));

        service.archiveRoom(new ArchiveRoomInput("synthetic-key-0002", ID));

        Room archived = rooms.stored.get(ID);
        assertEquals(NOW_IN_MILLIS, archived.deletedAt());
        assertEquals(WriteOperation.ARCHIVE_ROOM, rooms.writes.get(1).operation());
    }

    @Test
    void aReplayedArchiveWritesNothingMore() {
        service.createRoom(create(KEY));
        ArchiveRoomInput archive = new ArchiveRoomInput("synthetic-key-0002", ID);
        service.archiveRoom(archive);

        service.archiveRoom(archive);

        assertEquals(2, rooms.writes.size());
    }

    @Test
    void anArchiveOfAnUnknownOrArchivedRoomIsNotFound() {
        assertThrows(RoomNotFoundException.class,
                () -> service.archiveRoom(new ArchiveRoomInput(KEY, ID)));

        service.createRoom(create(KEY));
        service.archiveRoom(new ArchiveRoomInput("synthetic-key-0002", ID));

        assertThrows(RoomNotFoundException.class,
                () -> service.archiveRoom(new ArchiveRoomInput("synthetic-key-0003", ID)));
    }

    /** In-memory output port: it keeps the rooms and the keys the way the database would. */
    private static final class FakeRooms implements RoomRepository {

        final Map<UUID, Room> stored = new HashMap<>();
        final List<RoomWrite> writes = new ArrayList<>();
        boolean nameInUse;
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
        public Optional<Room> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public RoomPage findActive(int page, int limit) {
            List<Room> active = stored.values().stream().filter(room -> !room.isArchived()).toList();
            return new RoomPage(active, page, limit, active.size());
        }

        @Override
        public Room save(RoomWrite write) {
            if (nameInUse) {
                throw new RoomNameAlreadyInUseException();
            }
            stored.put(write.room().id(), write.room());
            hashByKey.put(write.idempotencyKey(), write.requestHash());
            resourceByKey.put(write.idempotencyKey(), write.room().id());
            writes.add(write);
            return write.room();
        }
    }
}
