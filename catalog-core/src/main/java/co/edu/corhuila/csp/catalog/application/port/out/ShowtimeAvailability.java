package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.domain.model.Seat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What Booking needs to validate a seat against a showtime (HU-CATALOG-005, ADR-042). The title and the room
 * name are the snapshots of the showtime; {@code seatLayout} is the current layout of the room, in the order it
 * is stored, and {@code seats} its labels.
 */
public record ShowtimeAvailability(UUID showtimeId, String movieTitle, String roomName, Instant startsAt,
        Instant endsAt, long priceCents, List<Seat> seatLayout) {

    public ShowtimeAvailability {
        seatLayout = List.copyOf(seatLayout);
    }

    public List<String> seats() {
        return seatLayout.stream().map(Seat::label).toList();
    }
}
