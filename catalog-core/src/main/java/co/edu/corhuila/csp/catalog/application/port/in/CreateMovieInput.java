package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import java.util.Objects;

/** What the administrator asks when creating a movie, with the key that makes the call repeatable. */
public record CreateMovieInput(String idempotencyKey, MovieDetails details, PublicationStatus status) {

    public CreateMovieInput {
        Objects.requireNonNull(idempotencyKey, "the idempotency key is required");
        Objects.requireNonNull(details, "the details are required");
        Objects.requireNonNull(status, "the status is required");
    }
}
