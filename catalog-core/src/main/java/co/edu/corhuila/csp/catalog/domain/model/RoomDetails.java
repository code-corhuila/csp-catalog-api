package co.edu.corhuila.csp.catalog.domain.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What describes a room: its name and its seat layout. Every invariant of the layout lives here, so
 * no room without seats or with a repeated label can exist; a malformed value throws
 * {@link InvalidRoomException}. It is immutable: {@link #with} returns a new instance.
 */
public record RoomDetails(String name, List<Seat> seats) {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_SEATS = 500;

    public RoomDetails {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidRoomException("the name is required and has at most 100 characters");
        }
        if (seats == null || seats.isEmpty() || seats.size() > MAX_SEATS) {
            throw new InvalidRoomException("a room has between 1 and 500 seats");
        }
        seats = List.copyOf(seats);
        requireUniqueLabels(seats);
    }

    /** The number of seats of the room. */
    public int capacity() {
        return seats.size();
    }

    /** Applies the fields that were sent; the result is validated like any other room. */
    public RoomDetails with(RoomChanges changes) {
        return new RoomDetails(
                changes.name() != null ? changes.name() : name,
                changes.seats() != null ? changes.seats() : seats);
    }

    private static void requireUniqueLabels(List<Seat> seats) {
        Set<String> labels = new HashSet<>();
        for (Seat seat : seats) {
            if (!labels.add(seat.label())) {
                throw new InvalidRoomException("the seat label " + seat.label() + " is repeated");
            }
        }
    }
}
