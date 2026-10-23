package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code AvailabilitySeat} of the contract: a seat of the room as Booking draws it. A seat that has no row or
 * no number leaves them out; it never sends them as null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AvailabilitySeatResponse(String label, String row, Integer number, SeatType type) {

    static AvailabilitySeatResponse of(Seat seat) {
        return new AvailabilitySeatResponse(seat.label(), seat.row(), seat.number(), seat.type());
    }
}
