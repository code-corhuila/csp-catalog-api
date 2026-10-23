package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import java.util.List;

/** {@code MovieListResponse} of the contract: the movies of the page and the {@code PaginatedMeta}. */
public record MovieListResponse(List<MovieResponse> data, PageMeta meta) {

    static MovieListResponse of(MoviePage page) {
        return new MovieListResponse(page.items().stream().map(MovieResponse::of).toList(),
                PageMeta.of(page.page(), page.limit(), page.total()));
    }
}
