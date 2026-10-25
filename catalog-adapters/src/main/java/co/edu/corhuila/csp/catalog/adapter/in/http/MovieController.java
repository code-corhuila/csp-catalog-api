package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.in.ArchiveMovieInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateMovieInput;
import co.edu.corhuila.csp.catalog.application.port.in.MovieUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateMovieInput;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The movie routes of {@code catalog-service.yaml}. It translates HTTP into the input port and back and holds
 * no rule of the domain: the permission is checked by the security filter, the invariants by the domain, and
 * every error is translated by {@link ApiExceptionHandler}. It answers DTOs, never an entity.
 */
@RestController
@RequestMapping("/movies")
public class MovieController {

    private final MovieUseCases movies;

    public MovieController(MovieUseCases movies) {
        this.movies = movies;
    }

    @PostMapping
    ResponseEntity<MovieResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateMovieRequest request,
            HttpServletRequest http) {
        Movie movie = movies.createMovie(
                new CreateMovieInput(IdempotencyKeys.checked(idempotencyKey), request.details(), request.requiredStatus()));
        URI location = URI.create(http.getContextPath() + "/movies/" + movie.id());
        return ResponseEntity.created(location).body(MovieResponse.of(movie));
    }

    @GetMapping
    MovieListResponse list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit) {
        return MovieListResponse.of(movies.listMovies(page, limit));
    }

    @GetMapping("/{movieId}")
    MovieResponse get(@PathVariable UUID movieId) {
        return MovieResponse.of(movies.getMovie(movieId));
    }

    @PatchMapping("/{movieId}")
    MovieResponse update(
            @PathVariable UUID movieId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody UpdateMovieRequest request) {
        return MovieResponse.of(
                movies.updateMovie(new UpdateMovieInput(IdempotencyKeys.checked(idempotencyKey), movieId, request.changes())));
    }

    @DeleteMapping("/{movieId}")
    ResponseEntity<Void> archive(@PathVariable UUID movieId, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        movies.archiveMovie(new ArchiveMovieInput(IdempotencyKeys.checked(idempotencyKey), movieId));
        return ResponseEntity.noContent().build();
    }
}
