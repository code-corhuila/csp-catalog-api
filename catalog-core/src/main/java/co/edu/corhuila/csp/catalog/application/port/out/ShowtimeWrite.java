package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimePublished;
import java.util.Objects;

/**
 * Everything the durable side needs for one showtime write: the showtime in its new state, the key that
 * makes the call repeatable (Norma 5.3.8), the hash that tells a replay from another request, whether the
 * room schedule must be checked for overlap and, only for a creation, the event to put in the outbox.
 */
public record ShowtimeWrite(Showtime showtime, WriteOperation operation, String idempotencyKey, String requestHash,
        boolean checksOverlap, ShowtimePublished eventToPublish) {

    public ShowtimeWrite {
        Objects.requireNonNull(showtime, "the showtime is required");
        Objects.requireNonNull(operation, "the operation is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("the idempotency key is required");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("the request hash is required");
        }
    }
}
