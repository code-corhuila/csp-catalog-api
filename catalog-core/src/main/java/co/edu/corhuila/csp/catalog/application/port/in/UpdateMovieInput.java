package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.domain.model.MovieChanges;
import java.util.Objects;
import java.util.UUID;

/** A partial update of one movie, with the key that makes the call repeatable. */
public record UpdateMovieInput(String idempotencyKey, UUID movieId, MovieChanges changes) {

    public UpdateMovieInput {
        Objects.requireNonNull(idempotencyKey, "the idempotency key is required");
        Objects.requireNonNull(movieId, "the movie id is required");
        Objects.requireNonNull(changes, "the changes are required");
    }
}
