package co.edu.corhuila.csp.catalog.domain.model;

import java.util.List;

/** A partial update of a room: a null field means "unchanged". */
public record RoomChanges(String name, List<Seat> seats) {
}
