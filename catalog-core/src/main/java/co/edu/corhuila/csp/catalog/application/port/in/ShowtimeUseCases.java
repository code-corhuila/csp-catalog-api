package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.application.port.out.ShowtimePage;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import java.util.UUID;

/** What the catalog offers about showtimes (HU-CATALOG-003, ADR-039). The HTTP adapter calls these and nothing else. */
public interface ShowtimeUseCases {

    /**
     * Schedules a showtime of a published movie in an active room. A replay of the same key and request
     * returns the stored showtime.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.ShowtimeReferenceException when the movie or the room
     *         does not exist, the movie is not published or the room is archived (422)
     * @throws co.edu.corhuila.csp.catalog.domain.model.InvalidShowtimeException when the interval, the price or
     *         a language breaks an invariant
     * @throws co.edu.corhuila.csp.catalog.domain.model.ShowtimeOverlapException when another scheduled showtime
     *         of the room overlaps the interval (409)
     * @throws co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException when the key
     *         was already used with another request
     */
    Showtime createShowtime(CreateShowtimeInput input);

    /**
     * The showtime with that id, whatever its status.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException when no showtime has that id
     */
    Showtime getShowtime(UUID showtimeId);

    /** Every showtime, cancelled ones included, ordered by start, in the window of {@code page} and {@code limit}. */
    ShowtimePage listShowtimes(int page, int limit);

    /**
     * Applies a partial update to a scheduled showtime.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException when no showtime has that id
     * @throws co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException when the showtime is
     *         not scheduled or the requested status is not allowed
     * @throws co.edu.corhuila.csp.catalog.domain.model.InvalidShowtimeException when the result breaks an invariant
     * @throws co.edu.corhuila.csp.catalog.domain.model.ShowtimeOverlapException when the new interval overlaps
     *         another scheduled showtime of the room (409)
     * @throws co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException when the key
     *         was already used with another request
     */
    Showtime updateShowtime(UpdateShowtimeInput input);

    /**
     * Cancels a scheduled showtime.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException when no showtime has that id
     * @throws co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException when the showtime is
     *         not scheduled
     * @throws co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException when the key
     *         was already used with another request
     */
    void cancelShowtime(CancelShowtimeInput input);
}
