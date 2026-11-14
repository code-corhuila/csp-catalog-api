package co.edu.corhuila.csp.catalog.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** The archive of one room, with the key that makes the call repeatable. */
public record ArchiveRoomInput(String idempotencyKey, UUID roomId) {

    public ArchiveRoomInput {
        Objects.requireNonNull(idempotencyKey, "the idempotency key is required");
        Objects.requireNonNull(roomId, "the room id is required");
    }
}
