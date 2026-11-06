package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.InvalidMovieException;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * The body of {@code POST /movies} ({@code CreateMovieRequest} of the contract). It only carries the values:
 * the invariants of a movie are judged by the domain, so a missing or invalid value is a 400 from there.
 */
public record CreateMovieRequest(
        String title,
        String synopsis,
        Integer durationMinutes,
        String classification,
        LocalDate releaseDate,
        List<String> genres,
        List<String> cast,
        URI posterUrl,
        PublicationStatus status) {

    MovieDetails details() {
        if (durationMinutes == null) {
            throw new InvalidMovieException("the duration is required");
        }
        return new MovieDetails(title, synopsis, durationMinutes, classification, releaseDate, genres, cast, posterUrl);
    }

    PublicationStatus requiredStatus() {
        if (status == null) {
            throw new InvalidMovieException("the status is required");
        }
        return status;
    }
}
