package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.domain.model.Room;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable side of the rooms. The room and its idempotency key are written in one database
 * transaction (Norma 5.3.8), stored together or not at all.
 */
public interface RoomRepository {

    /**
     * The room a previous call with this key produced or changed, so a replay answers without
     * evaluating the rules of the state again.
     *
     * @throws IdempotencyKeyConflictException when the key was already used with another request
     */
    Optional<UUID> findIdempotentResource(String idempotencyKey, String requestHash);

    /** The room with that id, archived or not: the use cases decide what an archived room means. */
    Optional<Room> findById(UUID roomId);

    /** The rooms that are not archived, in the window of {@code page} (from 1) and {@code limit}. */
    RoomPage findActive(int page, int limit);

    /**
     * Stores the room and the key together.
     *
     * @return the stored room, or the one a concurrent call with the same key and request already stored
     * @throws IdempotencyKeyConflictException when a concurrent call used the key with another request
     * @throws RoomNameAlreadyInUseException when another active room already has that name
     */
    Room save(RoomWrite write);
}
