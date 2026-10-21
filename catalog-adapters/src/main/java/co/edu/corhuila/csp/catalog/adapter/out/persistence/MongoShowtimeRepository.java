package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.application.port.out.ShowtimePage;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeRepository;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeOverlapException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeOverlapService;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeReferenceException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Durable side of the showtimes in MongoDB (ADR-039). The showtime, its {@code idempotency_key} and, for a
 * creation, its {@code outbox_event} are written in one transaction (Norma 5.3.8, 5.3.11). The transaction
 * starts by incrementing {@code schedule_version} of the room: two writers of one room then conflict on
 * that document, the loser is retried by {@link IdempotentMongoWrites#execute}, sees the showtime of the
 * winner and fails the overlap check. The overlap query uses {@code idx_showtimes_room_id_starts_at}; this
 * adapter creates no collection, index or migration of its own (Norma 5.2.1).
 */
public class MongoShowtimeRepository implements ShowtimeRepository {

    private static final String SHOWTIMES = "showtimes";
    private static final String ROOMS = "rooms";
    private static final String OUTBOX = "outbox_event";
    private static final int MAX_LIMIT = 100;

    private final MongoTemplate template;
    private final IdempotentMongoWrites writes;

    public MongoShowtimeRepository(MongoTemplate template) {
        this.template = template;
        this.writes = new IdempotentMongoWrites(template);
    }

    @Override
    public Optional<UUID> findIdempotentResource(String idempotencyKey, String requestHash) {
        return writes.findResource(idempotencyKey, requestHash);
    }

    @Override
    public Optional<Showtime> findById(UUID showtimeId) {
        return Optional.ofNullable(template.findById(showtimeId.toString(), Document.class, SHOWTIMES))
                .map(ShowtimeDocumentMapper::toShowtime);
    }

    @Override
    public ShowtimePage findAll(int page, int limit) {
        if (page < 1 || limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("page starts at 1 and limit is between 1 and " + MAX_LIMIT);
        }
        Query window = new Query()
                .with(Sort.by(Sort.Direction.ASC, "starts_at").and(Sort.by(Sort.Direction.ASC, "_id")))
                .skip((long) (page - 1) * limit)
                .limit(limit);
        List<Showtime> showtimes = template.find(window, Document.class, SHOWTIMES).stream()
                .map(ShowtimeDocumentMapper::toShowtime)
                .toList();
        return new ShowtimePage(showtimes, page, limit, template.count(new Query(), SHOWTIMES));
    }

    @Override
    public Showtime save(ShowtimeWrite write) {
        try {
            return writes.execute(write.idempotencyKey(), write.requestHash(), write.showtime(),
                    () -> store(write), this::findById);
        } catch (ShowtimeOverlapException overlap) {
            return replayStoredByAConcurrentCall(write).orElseThrow(() -> overlap);
        }
    }

    /**
     * A concurrent call with the same key and request may have stored its own showtime for the same
     * interval before this one wrote: that is a replay, not an overlap with another showtime.
     */
    private Optional<Showtime> replayStoredByAConcurrentCall(ShowtimeWrite write) {
        return writes.findResource(write.idempotencyKey(), write.requestHash()).flatMap(this::findById);
    }

    private void store(ShowtimeWrite write) {
        Showtime showtime = write.showtime();
        lockTheRoom(showtime.roomId());
        if (write.checksOverlap()) {
            ShowtimeOverlapService.requireNoOverlap(showtime, scheduledShowtimesTouching(showtime));
        }
        Document document = ShowtimeDocumentMapper.toDocument(showtime);
        if (write.operation() == WriteOperation.CREATE_SHOWTIME) {
            template.insert(document, SHOWTIMES);
        } else {
            template.save(document, SHOWTIMES);
        }
        if (write.eventToPublish() != null) {
            template.insert(ShowtimeDocumentMapper.toOutboxDocument(write.eventToPublish()), OUTBOX);
        }
        writes.insertKey(write.idempotencyKey(), write.operation(), write.requestHash(), showtime.id(),
                showtime.updatedAt());
    }

    /** Writes the room document, which serializes the writers of the showtimes of that room. */
    private void lockTheRoom(UUID roomId) {
        long matched = template.updateFirst(Query.query(Criteria.where("_id").is(roomId.toString())),
                new Update().inc("schedule_version", 1), ROOMS).getMatchedCount();
        if (matched == 0) {
            throw new ShowtimeReferenceException("the room does not exist");
        }
    }

    private List<Showtime> scheduledShowtimesTouching(Showtime candidate) {
        Query touching = Query.query(Criteria.where("room_id").is(candidate.roomId().toString())
                .and("status").is(ShowtimeStatus.SCHEDULED.name())
                .and("starts_at").lt(Date.from(candidate.interval().end()))
                .and("ends_at").gt(Date.from(candidate.interval().start())));
        return template.find(touching, Document.class, SHOWTIMES).stream()
                .map(ShowtimeDocumentMapper::toShowtime)
                .toList();
    }
}
