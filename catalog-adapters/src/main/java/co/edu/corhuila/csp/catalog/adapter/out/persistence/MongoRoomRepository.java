package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.application.port.out.RoomNameAlreadyInUseException;
import co.edu.corhuila.csp.catalog.application.port.out.RoomPage;
import co.edu.corhuila.csp.catalog.application.port.out.RoomRepository;
import co.edu.corhuila.csp.catalog.application.port.out.RoomWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Durable side of the rooms in MongoDB. The room and its {@code idempotency_key} are written in one
 * transaction (Norma 5.3.8, ADR-038), so a room never exists without its key nor a key without its
 * room. The unique index {@code idx_rooms_name} of {@code csp-catalog-db} decides a name already in use;
 * this adapter creates no collection, index or migration of its own (Norma 5.2.1).
 */
public class MongoRoomRepository implements RoomRepository {

    private static final String ROOMS = "rooms";
    private static final String NAME_INDEX = "idx_rooms_name";
    private static final int MAX_LIMIT = 100;

    private final MongoTemplate template;
    private final IdempotentMongoWrites writes;

    public MongoRoomRepository(MongoTemplate template) {
        this.template = template;
        this.writes = new IdempotentMongoWrites(template);
    }

    @Override
    public Optional<UUID> findIdempotentResource(String idempotencyKey, String requestHash) {
        return writes.findResource(idempotencyKey, requestHash);
    }

    @Override
    public Optional<Room> findById(UUID roomId) {
        return Optional.ofNullable(template.findById(roomId.toString(), Document.class, ROOMS))
                .map(RoomDocumentMapper::toRoom);
    }

    @Override
    public RoomPage findActive(int page, int limit) {
        if (page < 1 || limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("page starts at 1 and limit is between 1 and " + MAX_LIMIT);
        }
        Criteria active = Criteria.where("deleted_at").is(null);
        Query window = Query.query(active)
                .with(Sort.by(Sort.Direction.DESC, "created_at").and(Sort.by(Sort.Direction.DESC, "_id")))
                .skip((long) (page - 1) * limit)
                .limit(limit);
        List<Room> rooms = template.find(window, Document.class, ROOMS).stream()
                .map(RoomDocumentMapper::toRoom)
                .toList();
        return new RoomPage(rooms, page, limit, template.count(Query.query(active), ROOMS));
    }

    @Override
    public Room save(RoomWrite write) {
        try {
            return writes.execute(write.idempotencyKey(), write.requestHash(), write.room(),
                    () -> store(write), this::findById);
        } catch (RoomNameAlreadyInUseException nameInUse) {
            return replayStoredByAConcurrentCall(write).orElseThrow(() -> nameInUse);
        }
    }

    /**
     * A concurrent call with the same key and request may have stored the room, with the same name,
     * before this one wrote: that is a replay, not a name in use by another room.
     */
    private Optional<Room> replayStoredByAConcurrentCall(RoomWrite write) {
        return writes.findResource(write.idempotencyKey(), write.requestHash()).flatMap(this::findById);
    }

    private void store(RoomWrite write) {
        Document document = RoomDocumentMapper.toDocument(write.room());
        try {
            if (write.operation() == WriteOperation.CREATE_ROOM) {
                template.insert(document, ROOMS);
            } else {
                template.save(document, ROOMS);
            }
        } catch (DuplicateKeyException duplicate) {
            if (isNameIndex(duplicate)) {
                throw new RoomNameAlreadyInUseException();
            }
            throw duplicate;
        }
        writes.insertKey(write.idempotencyKey(), write.operation(), write.requestHash(), write.room().id(),
                write.room().updatedAt());
    }

    /** The error of a unique index names the index; a duplicate {@code _id} or key names another one. */
    private static boolean isNameIndex(DuplicateKeyException duplicate) {
        for (Throwable cause = duplicate; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(NAME_INDEX)) {
                return true;
            }
        }
        return false;
    }
}
