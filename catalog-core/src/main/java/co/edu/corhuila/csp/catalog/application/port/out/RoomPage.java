package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.domain.model.Room;
import java.util.List;

/** One window of the room list and the total the contract reports in its metadata. */
public record RoomPage(List<Room> items, int page, int limit, long total) {

    public RoomPage {
        items = List.copyOf(items);
    }
}
