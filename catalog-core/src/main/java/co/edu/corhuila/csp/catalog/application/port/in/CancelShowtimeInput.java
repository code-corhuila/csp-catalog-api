package co.edu.corhuila.csp.catalog.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** The cancellation of one showtime, with the key that makes the call repeatable. */
public record CancelShowtimeInput(String idempotencyKey, UUID showtimeId) {

    public CancelShowtimeInput {
        Objects.requireNonNull(idempotencyKey, "the idempotency key is required");
        Objects.requireNonNull(showtimeId, "the showtime id is required");
    }
}
