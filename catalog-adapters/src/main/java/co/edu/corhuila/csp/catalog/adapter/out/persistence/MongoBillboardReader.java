package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.application.port.in.BillboardQuery;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardEntry;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardMovie;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardPage;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardReader;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardRoom;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardShowtime;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * The public billboard of ADR-041 read from MongoDB with one aggregation on {@code showtimes}. The first
 * {@code $match} (status, end, start day and movie) is the one that uses {@code idx_showtimes_status_starts_at};
 * the movie and the room are then joined by id and only the published, not archived movie and the not archived
 * room are kept. A {@code $facet} returns the window and the total of the matching showtimes in one round trip.
 * The adapter only reads: it creates no collection, index or migration (Norma 5.2.1).
 */
public class MongoBillboardReader implements BillboardReader {

    private static final String SHOWTIMES = "showtimes";
    private static final String MOVIES = "movies";
    private static final String ROOMS = "rooms";

    private final MongoTemplate template;

    public MongoBillboardReader(MongoTemplate template) {
        this.template = template;
    }

    @Override
    public BillboardPage find(BillboardQuery query, Instant now) {
        Document result = template.getCollection(SHOWTIMES).aggregate(pipeline(query, now), Document.class).first();
        List<BillboardEntry> items = result.getList("items", Document.class).stream()
                .map(MongoBillboardReader::toEntry)
                .toList();
        List<Document> total = result.getList("total", Document.class);
        return new BillboardPage(items, query.page(), query.limit(),
                total.isEmpty() ? 0 : total.get(0).get("count", Number.class).longValue());
    }

    static List<Document> pipeline(BillboardQuery query, Instant now) {
        return List.of(
                new Document("$match", showtimeFilter(query, now)),
                join(MOVIES, "movie", "movie_id"),
                new Document("$match", new Document("movie.publication_status", PublicationStatus.PUBLISHED.name())
                        .append("movie.deleted_at", null)),
                join(ROOMS, "room", "room_id"),
                new Document("$match", new Document("room.deleted_at", null)),
                new Document("$facet", new Document("items", List.of(
                        new Document("$sort", new Document("starts_at", 1).append("_id", 1)),
                        new Document("$skip", (long) (query.page() - 1) * query.limit()),
                        new Document("$limit", query.limit())))
                        .append("total", List.of(new Document("$count", "count")))));
    }

    private static Document showtimeFilter(BillboardQuery query, Instant now) {
        Document filter = new Document("status", ShowtimeStatus.SCHEDULED.name())
                .append("ends_at", new Document("$gt", Date.from(now)));
        if (query.date() != null) {
            Instant dayStart = query.date().atStartOfDay(ZoneOffset.UTC).toInstant();
            filter.append("starts_at", new Document("$gte", Date.from(dayStart))
                    .append("$lt", Date.from(dayStart.plusSeconds(24 * 60 * 60))));
        }
        if (query.movieId() != null) {
            filter.append("movie_id", query.movieId().toString());
        }
        return filter;
    }

    private static Document join(String collection, String as, String localField) {
        return new Document("$lookup", new Document("from", collection).append("localField", localField)
                .append("foreignField", "_id").append("as", as));
    }

    private static BillboardEntry toEntry(Document row) {
        Movie movie = MovieDocumentMapper.toMovie(first(row, "movie"));
        Room room = RoomDocumentMapper.toRoom(first(row, "room"));
        Showtime showtime = ShowtimeDocumentMapper.toShowtime(row);
        return new BillboardEntry(
                toPublicMovie(movie),
                new BillboardRoom(room.id(), room.details().name(), room.details().capacity()),
                new BillboardShowtime(showtime.id(), showtime.interval().start(), showtime.interval().end(),
                        showtime.priceCents(), showtime.audioLanguage(), showtime.subtitleLanguage()));
    }

    /** The public data of a movie, shared by the billboard and the movie search (ADR-041, ADR-044). */
    static BillboardMovie toPublicMovie(Movie movie) {
        MovieDetails details = movie.details();
        return new BillboardMovie(movie.id(), details.title(), details.synopsis(), details.durationMinutes(),
                details.genres(), details.classification(), details.posterUrl(), details.releaseDate());
    }

    /** A {@code $lookup} on a unique id yields an array of exactly one document once the match has kept it. */
    private static Document first(Document row, String joined) {
        return row.getList(joined, Document.class).get(0);
    }
}
