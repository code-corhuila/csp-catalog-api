package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.BillboardMovie;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code BillboardMovie} of the contract: the public data of a published movie, with no status, cast or
 * timestamps. An optional field that has no value is left out, because the contract does not declare them
 * nullable. The poster is rendered as text.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BillboardMovieResponse(
        UUID id,
        String title,
        String synopsis,
        int durationMinutes,
        List<String> genres,
        String classification,
        String posterUrl,
        LocalDate releaseDate) {

    static BillboardMovieResponse of(BillboardMovie movie) {
        return new BillboardMovieResponse(movie.id(), movie.title(), movie.synopsis(), movie.durationMinutes(),
                movie.genres(), movie.classification(),
                movie.posterUrl() == null ? null : movie.posterUrl().toString(), movie.releaseDate());
    }
}
