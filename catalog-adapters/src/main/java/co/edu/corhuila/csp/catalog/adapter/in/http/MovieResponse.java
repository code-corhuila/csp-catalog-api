package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code Movie} of the contract: the id and the fields of the creation request. An optional field that has
 * no value is left out, because the contract does not declare them nullable. The entity never leaves the
 * adapter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MovieResponse(
        UUID id,
        String title,
        String synopsis,
        int durationMinutes,
        String classification,
        LocalDate releaseDate,
        List<String> genres,
        List<String> cast,
        URI posterUrl,
        String status) {

    static MovieResponse of(Movie movie) {
        MovieDetails details = movie.details();
        return new MovieResponse(movie.id(), details.title(), details.synopsis(), details.durationMinutes(),
                details.classification(), details.releaseDate(), details.genres(), details.cast(),
                details.posterUrl(), movie.status().name());
    }
}
