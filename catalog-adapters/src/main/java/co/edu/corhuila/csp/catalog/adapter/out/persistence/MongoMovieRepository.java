package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.MovieOperation;
import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.port.out.MovieWrite;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import com.mongodb.MongoException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable side of the movies in MongoDB. The movie and its {@code idempotency_key} are written in one
 * transaction (Norma 5.3.8, ADR-037), so a movie never exists without its key nor a key without its
 * movie. It writes documents the validator of {@code csp-catalog-db} accepts and creates no collection,
 * index or migration of its own (Norma 5.2.1).
 */
public class MongoMovieRepository implements MovieRepository {

    private static final String MOVIES = "movies";
    private static final String KEYS = "idempotency_key";
    private static final int MAX_LIMIT = 100;
    private static final int WRITE_ATTEMPTS = 10;
    private static final long WRITE_RETRY_PAUSE_MILLIS = 25;
    private static final int WRITE_CONFLICT = 112;

    private final MongoTemplate template;
    private final TransactionTemplate transaction;

    public MongoMovieRepository(MongoTemplate template) {
        this.template = template;
        this.transaction = new TransactionTemplate(new MongoTransactionManager(template.getMongoDatabaseFactory()));
    }

    @Override
    public Optional<UUID> findIdempotentResource(String idempotencyKey, String requestHash) {
        Document stored = template.findById(idempotencyKey, Document.class, KEYS);
        if (stored == null) {
            return Optional.empty();
        }
        if (!requestHash.equals(stored.getString("request_hash"))) {
            throw new IdempotencyKeyConflictException();
        }
        return Optional.of(UUID.fromString(stored.getString("resource_id")));
    }

    @Override
    public Optional<Movie> findById(UUID movieId) {
        return Optional.ofNullable(template.findById(movieId.toString(), Document.class, MOVIES))
                .map(MovieDocumentMapper::toMovie);
    }

    @Override
    public MoviePage findActive(int page, int limit) {
        if (page < 1 || limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("page starts at 1 and limit is between 1 and " + MAX_LIMIT);
        }
        Criteria active = Criteria.where("publication_status")
                .in(PublicationStatus.DRAFT.name(), PublicationStatus.PUBLISHED.name())
                .and("deleted_at").is(null);
        Query window = Query.query(active)
                .with(Sort.by(Sort.Direction.DESC, "created_at").and(Sort.by(Sort.Direction.DESC, "_id")))
                .skip((long) (page - 1) * limit)
                .limit(limit);
        List<Movie> movies = template.find(window, Document.class, MOVIES).stream()
                .map(MovieDocumentMapper::toMovie)
                .toList();
        return new MoviePage(movies, page, limit, template.count(Query.query(active), MOVIES));
    }

    @Override
    public Movie save(MovieWrite write) {
        for (int attempt = 1; ; attempt++) {
            try {
                transaction.executeWithoutResult(status -> store(write));
                return write.movie();
            } catch (RuntimeException failure) {
                if (!isConcurrentKeyWrite(failure) || attempt == WRITE_ATTEMPTS) {
                    throw failure;
                }
                Optional<UUID> stored = findIdempotentResource(write.idempotencyKey(), write.requestHash());
                if (stored.isPresent()) {
                    return findById(stored.get()).orElseThrow(() -> failure);
                }
                pause();
            }
        }
    }

    private void store(MovieWrite write) {
        Document document = MovieDocumentMapper.toDocument(write.movie());
        if (write.operation() == MovieOperation.CREATE_MOVIE) {
            template.insert(document, MOVIES);
        } else {
            template.save(document, MOVIES);
        }
        template.insert(new Document("_id", write.idempotencyKey())
                .append("operation", write.operation().name())
                .append("request_hash", write.requestHash())
                .append("resource_id", write.movie().id().toString())
                .append("created_at", Date.from(write.movie().updatedAt())), KEYS);
    }

    /**
     * Another call holds the same key: the key already exists, or the transaction of the other call has
     * not committed yet and MongoDB answers a write conflict. The loser looks the key up again.
     */
    private static boolean isConcurrentKeyWrite(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
            if (cause instanceof MongoException mongo
                    && (mongo.getCode() == WRITE_CONFLICT
                            || mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL))) {
                return true;
            }
        }
        return false;
    }

    private static void pause() {
        try {
            Thread.sleep(WRITE_RETRY_PAUSE_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a concurrent write of the same key",
                    interrupted);
        }
    }
}
