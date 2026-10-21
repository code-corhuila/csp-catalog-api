package co.edu.corhuila.csp.catalog.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What Booking needs to validate a seat against a showtime (HU-CATALOG-005, ADR-042). The title and the room
 * name are the snapshots of the showtime; {@code seats} are the labels of the current layout of the room, in
 * the order they are stored.
 */
public record ShowtimeAvailability(UUID showtimeId, String movieTitle, String roomName, Instant startsAt,
        Instant endsAt, long priceCents, List<String> seats) {

    public ShowtimeAvailability {
        seats = List.copyOf(seats);
    }
}
