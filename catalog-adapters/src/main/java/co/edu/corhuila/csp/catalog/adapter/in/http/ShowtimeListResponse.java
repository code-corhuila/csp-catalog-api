package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.ShowtimePage;
import java.util.List;

/** {@code ShowtimeListResponse} of the contract: the showtimes of the page and the {@code PaginatedMeta}. */
public record ShowtimeListResponse(List<ShowtimeResponse> data, PageMeta meta) {

    static ShowtimeListResponse of(ShowtimePage page) {
        return new ShowtimeListResponse(page.items().stream().map(ShowtimeResponse::of).toList(),
                PageMeta.of(page.page(), page.limit(), page.total()));
    }
}
