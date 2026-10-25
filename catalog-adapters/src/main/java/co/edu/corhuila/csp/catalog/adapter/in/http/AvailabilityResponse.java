package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeAvailability;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code AvailabilityResponse} of the contract: what Booking validates a seat against. {@code price} is the
 * price of the showtime in cents, an integer, {@code seats} the labels of the room and {@code seatLayout} its
 * seats with row, number and type, both in their stored order.
 */
public record AvailabilityResponse(
        UUID showtimeId,
        String movieTitle,
        String roomName,
        Instant startsAt,
        Instant endsAt,
        long price,
        List<String> seats,
        List<AvailabilitySeatResponse> seatLayout) {

    static AvailabilityResponse of(ShowtimeAvailability availability) {
        return new AvailabilityResponse(availability.showtimeId(), availability.movieTitle(),
                availability.roomName(), availability.startsAt(), availability.endsAt(),
                availability.priceCents(), availability.seats(),
                availability.seatLayout().stream().map(AvailabilitySeatResponse::of).toList());
    }
}
