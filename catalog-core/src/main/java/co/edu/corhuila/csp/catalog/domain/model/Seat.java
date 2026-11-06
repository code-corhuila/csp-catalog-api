package co.edu.corhuila.csp.catalog.domain.model;

/**
 * A seat of a room. The label identifies it inside the room; the row and the number are optional
 * hints for the seat map. A seat with no type is {@link SeatType#STANDARD}. A malformed seat throws
 * {@link InvalidRoomException}.
 */
public record Seat(String label, String row, Integer number, SeatType type) {

    public Seat {
        if (label == null || label.isBlank()) {
            throw new InvalidRoomException("the seat label is required");
        }
        if (row != null && row.isBlank()) {
            throw new InvalidRoomException("the seat row cannot be blank");
        }
        if (number != null && number < 1) {
            throw new InvalidRoomException("the seat number must be at least 1");
        }
        type = type == null ? SeatType.STANDARD : type;
    }
}
