package co.edu.corhuila.csp.catalog.application.port.in;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The window and the optional filters of a billboard query. */
class BillboardQueryTest {

    private static final LocalDate DATE = LocalDate.parse("2026-11-01");
    private static final UUID MOVIE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void theFiltersAreOptional() {
        BillboardQuery query = new BillboardQuery(null, null, 1, 20);

        assertNull(query.date());
        assertNull(query.movieId());
    }

    @Test
    void theFiltersAreKept() {
        BillboardQuery query = new BillboardQuery(DATE, MOVIE_ID, 2, 10);

        assertEquals(DATE, query.date());
        assertEquals(MOVIE_ID, query.movieId());
    }

    @Test
    void thePageStartsAtOne() {
        assertThrows(IllegalArgumentException.class, () -> new BillboardQuery(null, null, 0, 20));
        assertDoesNotThrow(() -> new BillboardQuery(null, null, 1, 20));
    }

    @Test
    void theLimitIsBetweenOneAndOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> new BillboardQuery(null, null, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BillboardQuery(null, null, 1, 101));
        assertDoesNotThrow(() -> new BillboardQuery(null, null, 1, 1));
        assertDoesNotThrow(() -> new BillboardQuery(null, null, 1, 100));
    }
}
