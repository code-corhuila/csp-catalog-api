package co.edu.corhuila.csp.catalog.application.usecase;

import co.edu.corhuila.csp.catalog.application.port.in.ArchiveRoomInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateRoomInput;
import co.edu.corhuila.csp.catalog.application.port.in.RoomUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateRoomInput;
import co.edu.corhuila.csp.catalog.application.port.out.IdGenerator;
import co.edu.corhuila.csp.catalog.application.port.out.RoomPage;
import co.edu.corhuila.csp.catalog.application.port.out.RoomRepository;
import co.edu.corhuila.csp.catalog.application.port.out.RoomWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.InvalidRoomException;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomChanges;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.RoomNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The use cases of the room aggregate (ADR-038). It depends only on the domain and on the ports, and it
 * asks the clock for the time. A write first looks for its key: a replay answers before the rules of the
 * current state are evaluated. An archived room is not found, so a replayed update of a room archived
 * afterwards answers not found, while a replayed archive answers without loading the room.
 */
public class RoomService implements RoomUseCases {

    private final RoomRepository rooms;
    private final IdGenerator ids;
    private final Clock clock;

    public RoomService(RoomRepository rooms, IdGenerator ids, Clock clock) {
        this.rooms = rooms;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Room createRoom(CreateRoomInput input) {
        RoomDetails d = input.details();
        String hash = IdempotentWrites.requestHash(WriteOperation.CREATE_ROOM, null, d.name(), d.seats(),
                input.declaredCapacity());
        return replayOr(input.idempotencyKey(), hash, () -> {
            requireCapacityOfSeats(input.declaredCapacity(), d);
            return rooms.save(new RoomWrite(Room.create(ids.next(), d, now()), WriteOperation.CREATE_ROOM,
                    input.idempotencyKey(), hash));
        });
    }

    @Override
    public Room getRoom(UUID roomId) {
        return rooms.findById(roomId)
                .filter(room -> !room.isArchived())
                .orElseThrow(() -> new RoomNotFoundException(roomId));
    }

    @Override
    public RoomPage listRooms(int page, int limit) {
        return rooms.findActive(page, limit);
    }

    @Override
    public Room updateRoom(UpdateRoomInput input) {
        RoomChanges c = input.changes();
        String hash = IdempotentWrites.requestHash(WriteOperation.UPDATE_ROOM, input.roomId(), c.name(), c.seats());
        return replayOr(input.idempotencyKey(), hash, () -> rooms.save(new RoomWrite(
                getRoom(input.roomId()).change(c, now()), WriteOperation.UPDATE_ROOM,
                input.idempotencyKey(), hash)));
    }

    @Override
    public void archiveRoom(ArchiveRoomInput input) {
        String hash = IdempotentWrites.requestHash(WriteOperation.ARCHIVE_ROOM, input.roomId());
        IdempotentWrites.replayOr(rooms.findIdempotentResource(input.idempotencyKey(), hash), id -> null,
                () -> rooms.save(new RoomWrite(getRoom(input.roomId()).archive(now()),
                        WriteOperation.ARCHIVE_ROOM, input.idempotencyKey(), hash)));
    }

    private Room replayOr(String idempotencyKey, String requestHash, Supplier<Room> write) {
        return IdempotentWrites.replayOr(rooms.findIdempotentResource(idempotencyKey, requestHash), this::getRoom,
                write);
    }

    private static void requireCapacityOfSeats(Integer declaredCapacity, RoomDetails details) {
        if (declaredCapacity != null && declaredCapacity != details.capacity()) {
            throw new InvalidRoomException("the capacity must equal the number of seats");
        }
    }

    /** The database keeps milliseconds: a replay must show the same instant the first answer did. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
