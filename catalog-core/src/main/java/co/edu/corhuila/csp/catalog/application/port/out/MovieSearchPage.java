package co.edu.corhuila.csp.catalog.application.port.out;

import java.util.List;

/** One window of the movie search and the total the contract reports in its metadata. */
public record MovieSearchPage(List<BillboardMovie> items, int page, int limit, long total) {

    public MovieSearchPage {
        items = List.copyOf(items);
    }
}
