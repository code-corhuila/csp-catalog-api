package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable side of the showtimes (ADR-039). A write is one database transaction (Norma 5.3.8, 5.3.11):
 * everything it stores is stored together or not at all.
 */
public interface ShowtimeRepository {

    /**
     * The showtime a previous call with this key produced or changed, so a replay answers without
     * evaluating the rules of the state again.
     *
     * @throws IdempotencyKeyConflictException when the key was already used with another request
     */
    Optional<UUID> findIdempotentResource(String idempotencyKey, String requestHash);

    Optional<Showtime> findById(UUID showtimeId);

    /** Every showtime, cancelled ones included, ordered by start, in the window of {@code page} (from 1) and {@code limit}. */
    ShowtimePage findAll(int page, int limit);

    /**
     * Stores the showtime in one database transaction. It increments {@code schedule_version} of the room of
     * the showtime, so concurrent schedulers of one room conflict; when {@code checksOverlap} it loads the
     * {@code SCHEDULED} showtimes of the room and calls
     * {@link co.edu.corhuila.csp.catalog.domain.model.ShowtimeOverlapService#requireNoOverlap}; then it stores
     * the showtime, the idempotency key and, if present, the outbox event.
     *
     * @return the stored showtime, or the one a concurrent call with the same key and request already stored
     * @throws IdempotencyKeyConflictException when the key was used with another request
     * @throws co.edu.corhuila.csp.catalog.domain.model.ShowtimeOverlapException when the overlap is checked
     *         and another scheduled showtime of the room overlaps
     */
    Showtime save(ShowtimeWrite write);
}
