package co.edu.corhuila.csp.catalog.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** The archive of one movie, with the key that makes the call repeatable. */
public record ArchiveMovieInput(String idempotencyKey, UUID movieId) {

    public ArchiveMovieInput {
        Objects.requireNonNull(idempotencyKey, "the idempotency key is required");
        Objects.requireNonNull(movieId, "the movie id is required");
    }
}
