package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.application.port.out.RoomPage;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import java.util.UUID;

/** What the catalog offers about rooms. The HTTP adapter calls these and nothing else. */
public interface RoomUseCases {

    /**
     * Creates a room with its seats. A replay of the same key and request returns the stored room.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.InvalidRoomException when the layout breaks an
     *         invariant or the declared capacity differs from the number of seats
     * @throws co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException when
     *         the key was already used with another request
     * @throws co.edu.corhuila.csp.catalog.application.port.out.RoomNameAlreadyInUseException when
     *         another active room has that name
     */
    Room createRoom(CreateRoomInput input);

    /**
     * The room with that id.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.RoomNotFoundException when no active room has
     *         that id: an archived room is not found
     */
    Room getRoom(UUID roomId);

    /** The rooms that are not archived, in the window of {@code page} and {@code limit}. */
    RoomPage listRooms(int page, int limit);

    /**
     * Applies a partial update.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.RoomNotFoundException when no active room has that id
     */
    Room updateRoom(UpdateRoomInput input);

    /**
     * Archives the room: it keeps existing, with its deletion date set.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.RoomNotFoundException when no active room has that id
     */
    void archiveRoom(ArchiveRoomInput input);
}
