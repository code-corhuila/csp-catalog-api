package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import com.mongodb.MongoException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The idempotent write machinery the repositories of the catalog share (Norma 5.3.8, ADR-037, ADR-038):
 * the lookup of an {@code idempotency_key}, the key document written in the same transaction as the
 * resource, and the retry of a call that lost the race for the same key to a concurrent one.
 */
final class IdempotentMongoWrites {

    private static final String KEYS = "idempotency_key";
    private static final int WRITE_ATTEMPTS = 10;
    private static final long WRITE_RETRY_PAUSE_MILLIS = 25;
    private static final int WRITE_CONFLICT = 112;

    private final MongoTemplate template;
    private final TransactionTemplate transaction;

    IdempotentMongoWrites(MongoTemplate template) {
        this.template = template;
        this.transaction = new TransactionTemplate(new MongoTransactionManager(template.getMongoDatabaseFactory()));
    }

    /**
     * The resource a previous call with this key produced.
     *
     * @throws IdempotencyKeyConflictException when the key was already used with another request
     */
    Optional<UUID> findResource(String key, String requestHash) {
        Document stored = template.findById(key, Document.class, KEYS);
        if (stored == null) {
            return Optional.empty();
        }
        if (!requestHash.equals(stored.getString("request_hash"))) {
            throw new IdempotencyKeyConflictException();
        }
        return Optional.of(UUID.fromString(stored.getString("resource_id")));
    }

    /** Records the key; it is called by the store step, inside the transaction of the resource. */
    void insertKey(String key, WriteOperation operation, String requestHash, UUID resourceId, Instant at) {
        template.insert(new Document("_id", key)
                .append("operation", operation.name())
                .append("request_hash", requestHash)
                .append("resource_id", resourceId.toString())
                .append("created_at", Date.from(at)), KEYS);
    }

    /**
     * Runs the store step in one transaction. When a concurrent call holds the same key, it returns the
     * resource that call stored; any other failure is rethrown at once.
     */
    <T> T execute(String key, String requestHash, T written, Runnable store, Function<UUID, Optional<T>> load) {
        for (int attempt = 1; ; attempt++) {
            try {
                transaction.executeWithoutResult(status -> store.run());
                return written;
            } catch (RuntimeException failure) {
                if (!isConcurrentKeyWrite(failure) || attempt == WRITE_ATTEMPTS) {
                    throw failure;
                }
                Optional<UUID> stored = findResource(key, requestHash);
                if (stored.isPresent()) {
                    return load.apply(stored.get()).orElseThrow(() -> failure);
                }
                pause();
            }
        }
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
