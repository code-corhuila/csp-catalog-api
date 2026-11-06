package co.edu.corhuila.csp.catalog.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The filters and the window of a billboard query. The date and the movie are optional: {@code null} means no
 * filter.
 */
public record BillboardQuery(LocalDate date, UUID movieId, int page, int limit) {

    public static final int MAX_LIMIT = 100;

    public BillboardQuery {
        if (page < 1 || limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("page starts at 1 and limit is between 1 and " + MAX_LIMIT);
        }
    }
}
