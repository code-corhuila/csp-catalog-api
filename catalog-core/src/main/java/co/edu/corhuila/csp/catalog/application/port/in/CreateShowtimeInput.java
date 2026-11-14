package co.edu.corhuila.csp.catalog.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The schedule of one showtime, with the key that makes the call repeatable. */
public record CreateShowtimeInput(String idempotencyKey, UUID movieId, UUID roomId, Instant startsAt, Instant endsAt,
        long priceCents, String audioLanguage, String subtitleLanguage) {

    public CreateShowtimeInput {
        Objects.requireNonNull(idempotencyKey, "the idempotency key is required");
        Objects.requireNonNull(movieId, "the movie id is required");
        Objects.requireNonNull(roomId, "the room id is required");
        Objects.requireNonNull(startsAt, "the start is required");
        Objects.requireNonNull(endsAt, "the end is required");
    }
}
