package co.edu.corhuila.csp.catalog.adapter.in.http;

/** {@code PaginatedMeta} of {@code _shared.yaml}: the window of a list and how many pages the total makes. */
public record PageMeta(int page, int limit, long total, long totalPages) {

    /** The metadata of a window; the ports guarantee that {@code limit} is at least 1. */
    static PageMeta of(int page, int limit, long total) {
        return new PageMeta(page, limit, total, (total + limit - 1) / limit);
    }
}
