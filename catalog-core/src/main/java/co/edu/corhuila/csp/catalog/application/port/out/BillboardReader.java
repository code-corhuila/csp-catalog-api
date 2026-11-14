package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.application.port.in.BillboardQuery;
import java.time.Instant;

/** Reads the public billboard from the store (ADR-041). */
public interface BillboardReader {

    /**
     * The window of the billboard that answers the query. The contract of ADR-041:
     * <ul>
     *   <li>only showtimes in status {@code SCHEDULED} whose {@code endsAt} is after {@code now}, of a movie
     *       that is {@code PUBLISHED} and not archived and of a room that is not archived;</li>
     *   <li>if {@code date} is set, only showtimes whose {@code startsAt} falls in that UTC calendar day;</li>
     *   <li>if {@code movieId} is set, only the showtimes of that movie;</li>
     *   <li>ordered by {@code startsAt} ascending and then by showtime id;</li>
     *   <li>{@code total} counts the matching showtimes, not only the ones of the window.</li>
     * </ul>
     */
    BillboardPage find(BillboardQuery query, Instant now);
}
