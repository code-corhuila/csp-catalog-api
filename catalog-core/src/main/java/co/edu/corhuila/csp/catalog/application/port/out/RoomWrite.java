package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.domain.model.Room;
import java.util.Objects;

/**
 * Everything the durable side needs for one room write: the room in its new state, the key that
 * makes the call repeatable (Norma 5.3.8) and the hash that tells a replay from another request.
 */
public record RoomWrite(Room room, WriteOperation operation, String idempotencyKey, String requestHash) {

    public RoomWrite {
        Objects.requireNonNull(room, "the room is required");
        Objects.requireNonNull(operation, "the operation is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("the idempotency key is required");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("the request hash is required");
        }
    }
}
