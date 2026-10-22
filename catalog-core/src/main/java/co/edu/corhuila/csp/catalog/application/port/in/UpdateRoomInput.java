package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.domain.model.RoomChanges;
import java.util.Objects;
import java.util.UUID;

/** A partial update of one room, with the key that makes the call repeatable. */
public record UpdateRoomInput(String idempotencyKey, UUID roomId, RoomChanges changes) {

    public UpdateRoomInput {
        Objects.requireNonNull(idempotencyKey, "the idempotency key is required");
        Objects.requireNonNull(roomId, "the room id is required");
        Objects.requireNonNull(changes, "the changes are required");
    }
}
