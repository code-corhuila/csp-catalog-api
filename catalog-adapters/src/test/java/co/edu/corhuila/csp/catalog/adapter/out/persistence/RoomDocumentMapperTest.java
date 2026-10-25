package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** The document the validator of catalog_db.rooms expects, and the way back (ADR-016, ADR-038). */
class RoomDocumentMapperTest {

    private static final UUID ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");

    private static Room full() {
        return Room.create(ID, new RoomDetails("Synthetic Room", List.of(
                new Seat("A1", "A", 1, SeatType.STANDARD),
                new Seat("A2", "A", 2, SeatType.VIP),
                new Seat("B1", "B", 1, SeatType.ACCESSIBLE))), NOW);
    }

    private static Room bare() {
        return Room.create(ID, new RoomDetails("Synthetic Room", List.of(new Seat("S1", null, null, null))), NOW);
    }

    @Test
    void theDocumentUsesTheFieldNamesAndTypesOfTheValidator() {
        Document document = RoomDocumentMapper.toDocument(full());

        assertEquals(Set.of("_id", "name", "capacity", "seats", "created_at", "updated_at", "deleted_at"),
                document.keySet());
        assertEquals(ID.toString(), document.get("_id"));
        assertEquals("Synthetic Room", document.get("name"));
        assertInstanceOf(Integer.class, document.get("capacity"));
        assertEquals(3, document.get("capacity"));
        assertEquals(Date.from(NOW), document.get("created_at"));
        assertEquals(Date.from(NOW), document.get("updated_at"));
        assertNull(document.get("deleted_at"));
    }

    @Test
    void theSeatsAreSubDocumentsWithTheFieldNamesOfTheValidator() {
        List<?> seats = (List<?>) RoomDocumentMapper.toDocument(full()).get("seats");

        Document second = (Document) seats.get(1);
        assertEquals(Set.of("seat_number", "row", "column", "type"), second.keySet());
        assertEquals("A2", second.get("seat_number"));
        assertEquals("A", second.get("row"));
        assertInstanceOf(Integer.class, second.get("column"));
        assertEquals(2, second.get("column"));
        assertEquals("VIP", second.get("type"));
        assertEquals("ACCESSIBLE", ((Document) seats.get(2)).get("type"));
    }

    @Test
    void aSeatWithoutRowAndNumberIsWrittenWithNullsAndTheStandardType() {
        List<?> seats = (List<?>) RoomDocumentMapper.toDocument(bare()).get("seats");

        Document seat = (Document) seats.get(0);
        assertEquals("S1", seat.get("seat_number"));
        assertNull(seat.get("row"));
        assertNull(seat.get("column"));
        assertEquals("STANDARD", seat.get("type"));
    }

    @Test
    void aFullRoomIsRebuiltIdenticallyFromItsDocument() {
        Room room = full();

        assertEquals(room, RoomDocumentMapper.toRoom(RoomDocumentMapper.toDocument(room)));
    }

    @Test
    void aRoomWhoseSeatsHaveNoRowAndNoNumberIsRebuiltIdentically() {
        Room room = bare();

        assertEquals(room, RoomDocumentMapper.toRoom(RoomDocumentMapper.toDocument(room)));
    }

    @Test
    void anArchivedRoomKeepsItsDeletionDate() {
        Room archived = full().archive(NOW.plusSeconds(60));

        Document document = RoomDocumentMapper.toDocument(archived);

        assertEquals(Date.from(NOW.plusSeconds(60)), document.get("deleted_at"));
        assertEquals(archived, RoomDocumentMapper.toRoom(document));
    }

    @Test
    void aSeatWithoutTypeInTheDocumentIsStandard() {
        Document document = RoomDocumentMapper.toDocument(bare());
        ((Document) ((List<?>) document.get("seats")).get(0)).remove("type");

        Room room = RoomDocumentMapper.toRoom(document);

        assertEquals(SeatType.STANDARD, room.details().seats().get(0).type());
    }
}
