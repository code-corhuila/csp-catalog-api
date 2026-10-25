package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * {@code Room} of the contract: the id and the fields of the creation request, with the capacity the domain
 * computes from the seats. The entity never leaves the adapter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomResponse(UUID id, String name, int capacity, List<SeatDto> seats) {

    static RoomResponse of(Room room) {
        RoomDetails details = room.details();
        return new RoomResponse(room.id(), details.name(), details.capacity(),
                details.seats().stream().map(SeatDto::of).toList());
    }
}
