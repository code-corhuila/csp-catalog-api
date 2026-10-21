package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bson.Document;

/**
 * The room as the document the validator of {@code catalog_db.rooms} expects, and the way back. Field
 * names are the snake_case of the data model; the capacity and the seat column are {@code int} and every
 * date a BSON date, because the validator checks those types. The capacity is derived from the seats.
 */
final class RoomDocumentMapper {

    private RoomDocumentMapper() {
    }

    static Document toDocument(Room room) {
        RoomDetails details = room.details();
        return new Document("_id", room.id().toString())
                .append("name", details.name())
                .append("capacity", details.capacity())
                .append("seats", details.seats().stream().map(RoomDocumentMapper::toDocument).toList())
                .append("created_at", Date.from(room.createdAt()))
                .append("updated_at", Date.from(room.updatedAt()))
                .append("deleted_at", room.deletedAt() == null ? null : Date.from(room.deletedAt()));
    }

    static Room toRoom(Document document) {
        List<Seat> seats = document.getList("seats", Document.class).stream()
                .map(RoomDocumentMapper::toSeat)
                .toList();
        return new Room(
                UUID.fromString(document.getString("_id")),
                new RoomDetails(document.getString("name"), seats),
                document.getDate("created_at").toInstant(),
                document.getDate("updated_at").toInstant(),
                instant(document.getDate("deleted_at")));
    }

    private static Document toDocument(Seat seat) {
        return new Document("seat_number", seat.label())
                .append("row", seat.row())
                .append("column", seat.number())
                .append("type", seat.type().name());
    }

    private static Seat toSeat(Document seat) {
        String type = seat.getString("type");
        return new Seat(
                seat.getString("seat_number"),
                seat.getString("row"),
                seat.getInteger("column"),
                type == null ? SeatType.STANDARD : SeatType.valueOf(type));
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
