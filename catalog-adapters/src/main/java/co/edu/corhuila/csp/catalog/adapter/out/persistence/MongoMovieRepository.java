package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.port.out.MovieWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Durable side of the movies in MongoDB. The movie and its {@code idempotency_key} are written in one
 * transaction (Norma 5.3.8, ADR-037), so a movie never exists without its key nor a key without its
 * movie. It writes documents the validator of {@code csp-catalog-db} accepts and creates no collection,
 * index or migration of its own (Norma 5.2.1).
 */
public class MongoMovieRepository implements MovieRepository {

    private static final String MOVIES = "movies";
    private static final int MAX_LIMIT = 100;

    private final MongoTemplate template;
    private final IdempotentMongoWrites writes;

    public MongoMovieRepository(MongoTemplate template) {
        this.template = template;
        this.writes = new IdempotentMongoWrites(template);
    }

    @Override
    public Optional<UUID> findIdempotentResource(String idempotencyKey, String requestHash) {
        return writes.findResource(idempotencyKey, requestHash);
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
        return writes.execute(write.idempotencyKey(), write.requestHash(), write.movie(),
                () -> store(write), this::findById);
    }

    private void store(MovieWrite write) {
        Document document = MovieDocumentMapper.toDocument(write.movie());
        if (write.operation() == WriteOperation.CREATE_MOVIE) {
            template.insert(document, MOVIES);
        } else {
            template.save(document, MOVIES);
        }
        writes.insertKey(write.idempotencyKey(), write.operation(), write.requestHash(), write.movie().id(),
                write.movie().updatedAt());
    }
}
