package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import java.util.List;

/** {@code MovieListResponse} of the contract: the movies of the page and the {@code PaginatedMeta}. */
public record MovieListResponse(List<MovieResponse> data, Meta meta) {

    public record Meta(int page, int limit, long total, long totalPages) {
    }

    static MovieListResponse of(MoviePage page) {
        long totalPages = (page.total() + page.limit() - 1) / page.limit();
        return new MovieListResponse(page.items().stream().map(MovieResponse::of).toList(),
                new Meta(page.page(), page.limit(), page.total(), totalPages));
    }
}
