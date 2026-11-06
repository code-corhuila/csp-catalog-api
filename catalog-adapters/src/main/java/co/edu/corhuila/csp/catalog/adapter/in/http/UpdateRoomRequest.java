package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.RoomChanges;
import java.util.List;

/** The body of {@code PATCH /rooms/{roomId}} ({@code UpdateRoomRequest}): a field that is not sent is unchanged. */
public record UpdateRoomRequest(String name, List<SeatDto> seats) {

    RoomChanges changes() {
        return new RoomChanges(name, seats == null ? null : seats.stream().map(SeatDto::toSeat).toList());
    }
}
