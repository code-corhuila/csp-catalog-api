package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.domain.model.Movie;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable side of the movies. The movie and its idempotency key are written in one database
 * transaction (Norma 5.3.8), stored together or not at all.
 */
public interface MovieRepository {

    /**
     * The movie a previous call with this key produced or changed, so a replay answers without
     * evaluating the rules of the state again.
     *
     * @throws IdempotencyKeyConflictException when the key was already used with another request
     */
    Optional<UUID> findIdempotentResource(String idempotencyKey, String requestHash);

    Optional<Movie> findById(UUID movieId);

    /** The movies that are not archived, newest first, in the window of {@code page} (from 1) and {@code limit}. */
    MoviePage findActive(int page, int limit);

    /**
     * Stores the movie and the key together.
     *
     * @return the stored movie, or the one a concurrent call with the same key and request already stored
     * @throws IdempotencyKeyConflictException when a concurrent call used the key with another request
     */
    Movie save(MovieWrite write);
}
