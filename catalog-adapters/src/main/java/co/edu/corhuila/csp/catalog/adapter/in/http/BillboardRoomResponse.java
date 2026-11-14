package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.BillboardRoom;
import java.util.UUID;

/** {@code BillboardRoom} of the contract: the public data of a room, with no seat layout. */
public record BillboardRoomResponse(UUID id, String name, int capacity) {

    static BillboardRoomResponse of(BillboardRoom room) {
        return new BillboardRoomResponse(room.id(), room.name(), room.capacity());
    }
}
