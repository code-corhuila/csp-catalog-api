package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchPage;
import java.util.List;

/** The 200 answer of {@code searchMovies}: the public movies of the page and the {@code PaginatedMeta}. */
public record MovieSearchResponse(List<BillboardMovieResponse> data, PageMeta meta) {

    static MovieSearchResponse of(MovieSearchPage page) {
        return new MovieSearchResponse(page.items().stream().map(BillboardMovieResponse::of).toList(),
                PageMeta.of(page.page(), page.limit(), page.total()));
    }
}
