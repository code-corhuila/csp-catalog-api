package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.MovieChanges;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** The body of {@code PATCH /movies/{movieId}} ({@code UpdateMovieRequest}): a field that is not sent is unchanged. */
public record UpdateMovieRequest(
        String title,
        String synopsis,
        Integer durationMinutes,
        String classification,
        LocalDate releaseDate,
        List<String> genres,
        List<String> cast,
        URI posterUrl,
        PublicationStatus status) {

    MovieChanges changes() {
        return new MovieChanges(title, synopsis, durationMinutes, classification, releaseDate, genres, cast,
                posterUrl, status);
    }
}
