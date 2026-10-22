package co.edu.corhuila.csp.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchQuery;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardMovie;
import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchPage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The movie search use case, with a fake of the output port (Annex C). */
class MovieSearchServiceTest {

    private static final UUID MOVIE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final FakeReader reader = new FakeReader();
    private final MovieSearchService service = new MovieSearchService(reader);

    @Test
    void thePortReceivesTheQueryUnchanged() {
        MovieSearchQuery query = new MovieSearchQuery("space", "Ciencia ficción", 150, 2, 10);

        service.search(query);

        assertSame(query, reader.query);
        assertEquals(1, reader.calls);
    }

    @Test
    void theAnswerIsThePageOfThePortUntouched() {
        MovieSearchPage page = new MovieSearchPage(List.of(movie()), 1, 20, 1);
        reader.page = page;

        assertSame(page, service.search(new MovieSearchQuery(null, null, null, 1, 20)));
    }

    @Test
    void aFailureOfThePortPropagates() {
        reader.failure = new IllegalStateException("the store is down");

        assertThrows(IllegalStateException.class,
                () -> service.search(new MovieSearchQuery(null, null, null, 1, 20)));
    }

    private static BillboardMovie movie() {
        return new BillboardMovie(MOVIE_ID, "Synthetic Movie", null, 120, List.of(), "PG-13", null, null);
    }

    private static final class FakeReader implements MovieSearchReader {

        MovieSearchQuery query;
        int calls;
        MovieSearchPage page = new MovieSearchPage(List.of(), 1, 20, 0);
        RuntimeException failure;

        @Override
        public MovieSearchPage find(MovieSearchQuery query) {
            this.query = query;
            calls++;
            if (failure != null) {
                throw failure;
            }
            return page;
        }
    }
}
