package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeAvailability;
import java.util.UUID;

/** What the catalog offers to Booking about a showtime (HU-CATALOG-005, ADR-042). The HTTP adapter calls this and nothing else. */
public interface AvailabilityUseCases {

    /**
     * The data Booking validates a seat against: the showtime, its price and the seat labels of its room.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException when the showtime is unknown or
     *         not available: it is not scheduled, or its movie is not published, or its room is archived
     */
    ShowtimeAvailability getAvailability(UUID showtimeId);
}
