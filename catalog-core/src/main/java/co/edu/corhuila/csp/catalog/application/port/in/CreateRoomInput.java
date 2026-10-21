package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import java.util.Objects;

/**
 * What the administrator asks when creating a room, with the key that makes the call repeatable. The
 * declared capacity is optional: when it is sent it must equal the number of seats.
 */
public record CreateRoomInput(String idempotencyKey, RoomDetails details, Integer declaredCapacity) {

    public CreateRoomInput {
        Objects.requireNonNull(idempotencyKey, "the idempotency key is required");
        Objects.requireNonNull(details, "the details are required");
    }
}
