package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import org.bson.Document;

/**
 * The movie as the document the validator of {@code catalog_db.movies} expects, and the way back.
 * Field names are the snake_case of the data model; the duration is an {@code int} and every date a
 * BSON date, because the validator checks those types. The domain never sees a {@link Document}.
 */
final class MovieDocumentMapper {

    private MovieDocumentMapper() {
    }

    static Document toDocument(Movie movie) {
        MovieDetails details = movie.details();
        return new Document("_id", movie.id().toString())
                .append("title", details.title())
                .append("synopsis", details.synopsis())
                .append("duration_minutes", details.durationMinutes())
                .append("classification", details.classification())
                .append("release_date", details.releaseDate() == null ? null : date(details.releaseDate()))
                .append("genres", details.genres())
                .append("cast", details.cast())
                .append("poster_url", details.posterUrl() == null ? null : details.posterUrl().toString())
                .append("publication_status", movie.status().name())
                .append("created_at", Date.from(movie.createdAt()))
                .append("updated_at", Date.from(movie.updatedAt()))
                .append("deleted_at", movie.deletedAt() == null ? null : Date.from(movie.deletedAt()));
    }

    static Movie toMovie(Document document) {
        Date releaseDate = document.getDate("release_date");
        String posterUrl = document.getString("poster_url");
        MovieDetails details = new MovieDetails(
                document.getString("title"),
                document.getString("synopsis"),
                document.getInteger("duration_minutes"),
                document.getString("classification"),
                releaseDate == null ? null : releaseDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
                document.getList("genres", String.class),
                document.getList("cast", String.class),
                posterUrl == null ? null : URI.create(posterUrl));
        return new Movie(
                UUID.fromString(document.getString("_id")),
                details,
                PublicationStatus.valueOf(document.getString("publication_status")),
                document.getDate("created_at").toInstant(),
                document.getDate("updated_at").toInstant(),
                instant(document.getDate("deleted_at")));
    }

    private static Date date(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
