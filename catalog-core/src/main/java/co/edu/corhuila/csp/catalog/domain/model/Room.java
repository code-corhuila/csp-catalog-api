package co.edu.corhuila.csp.catalog.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A room of the cinema and its seat layout (HU-CATALOG-002). It is immutable: every change returns a
 * new instance, so a failed rule changes nothing. Archiving sets {@code deletedAt} and never removes
 * the room, because showtimes keep referencing it; an archived room is not found.
 *
 * <p>A malformed room throws {@link InvalidRoomException} from the constructor.
 */
public record Room(
        UUID id,
        RoomDetails details,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public Room {
        if (id == null || details == null || createdAt == null || updatedAt == null) {
            throw new InvalidRoomException("a room needs an id, its details and its dates");
        }
    }

    /** A new active room. */
    public static Room create(UUID id, RoomDetails details, Instant now) {
        return new Room(id, details, now, now, null);
    }

    public boolean isArchived() {
        return deletedAt != null;
    }

    public Room archive(Instant now) {
        requireActive();
        return new Room(id, details, createdAt, now, now);
    }

    /** Applies a partial update; an archived room is not found. */
    public Room change(RoomChanges changes, Instant now) {
        requireActive();
        return new Room(id, details.with(changes), createdAt, now, deletedAt);
    }

    private void requireActive() {
        if (isArchived()) {
            throw new RoomNotFoundException(id);
        }
    }
}
