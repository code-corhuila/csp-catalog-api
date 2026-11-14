package co.edu.corhuila.csp.catalog.domain.model;

import java.util.UUID;

/** No active room has that id; an archived room is not found. */
public class RoomNotFoundException extends DomainException {

    public RoomNotFoundException(UUID roomId) {
        super("Room " + roomId + " was not found");
    }
}
