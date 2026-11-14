package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchQuery;
import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchUseCases;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public movie search route of {@code catalog-service.yaml} (ADR-044). It translates the query parameters
 * into the input port and back and holds no rule of the domain: the route is open in the security filter, the
 * validation of the filters belongs to {@link MovieSearchQuery}, the selection of the movies to the use case, and
 * every error is translated by {@link ApiExceptionHandler}. It answers DTOs, never an entity.
 */
@RestController
@RequestMapping("/billboard/movies")
public class MovieSearchController {

    private final MovieSearchUseCases search;

    public MovieSearchController(MovieSearchUseCases search) {
        this.search = search;
    }

    @GetMapping
    MovieSearchResponse get(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer durationMax,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return MovieSearchResponse.of(search.search(new MovieSearchQuery(q, genre, durationMax, page, limit)));
    }
}
