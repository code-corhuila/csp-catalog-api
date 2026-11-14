package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.domain.model.ShowtimeChanges;
import java.util.Objects;
import java.util.UUID;

/** A partial update of one showtime, with the key that makes the call repeatable. */
public record UpdateShowtimeInput(String idempotencyKey, UUID showtimeId, ShowtimeChanges changes) {

    public UpdateShowtimeInput {
        Objects.requireNonNull(idempotencyKey, "the idempotency key is required");
        Objects.requireNonNull(showtimeId, "the showtime id is required");
        Objects.requireNonNull(changes, "the changes are required");
    }
}
