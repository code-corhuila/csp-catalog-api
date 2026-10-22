package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code Seat} of the contract, in a request and in an answer. A seat without row or number leaves them out of
 * the answer, and its type is always written because the domain defaults it to {@code STANDARD}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeatDto(String label, String row, Integer number, SeatType type) {

    Seat toSeat() {
        return new Seat(label, row, number, type);
    }

    static SeatDto of(Seat seat) {
        return new SeatDto(seat.label(), seat.row(), seat.number(), seat.type());
    }
}
