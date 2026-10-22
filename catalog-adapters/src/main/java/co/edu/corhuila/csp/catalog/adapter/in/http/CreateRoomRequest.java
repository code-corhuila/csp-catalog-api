package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import java.util.List;

/**
 * The body of {@code POST /rooms} ({@code CreateRoomRequest} of the contract). It only carries the values: the
 * invariants of a room are judged by the domain, so a missing name or seat list is a 400 from there. The
 * capacity is the optional one the client declares; the use case compares it with the number of seats.
 */
public record CreateRoomRequest(String name, Integer capacity, List<SeatDto> seats) {

    RoomDetails details() {
        return new RoomDetails(name, seats == null ? null : seats.stream().map(SeatDto::toSeat).toList());
    }
}
