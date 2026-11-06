package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.domain.model.Movie;
import java.util.List;

/** One window of the movie list and the total the contract reports in its metadata. */
public record MoviePage(List<Movie> items, int page, int limit, long total) {

    public MoviePage {
        items = List.copyOf(items);
    }
}
