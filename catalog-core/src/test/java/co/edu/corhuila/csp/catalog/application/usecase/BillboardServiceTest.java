package co.edu.corhuila.csp.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.csp.catalog.application.port.in.BillboardQuery;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardEntry;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardMovie;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardPage;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardReader;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardRoom;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardShowtime;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The billboard use case, with a fake of the output port (Annex C). */
class BillboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123456789Z");
    private static final Instant NOW_IN_MILLIS = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final UUID MOVIE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ROOM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SHOWTIME_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private final FakeReader reader = new FakeReader();
    private final BillboardService service = new BillboardService(reader, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void thePortReceivesTheQueryUnchanged() {
        BillboardQuery query = new BillboardQuery(LocalDate.parse("2026-11-01"), MOVIE_ID, 2, 10);

        service.getBillboard(query);

        assertSame(query, reader.query);
    }

    @Test
    void thePortReceivesTheInstantOfTheClockInMilliseconds() {
        service.getBillboard(new BillboardQuery(null, null, 1, 20));

        assertEquals(NOW_IN_MILLIS, reader.now);
    }

    @Test
    void theAnswerIsThePageOfThePortUntouched() {
        BillboardPage page = new BillboardPage(List.of(entry()), 1, 20, 1);
        reader.page = page;

        assertSame(page, service.getBillboard(new BillboardQuery(null, null, 1, 20)));
    }

    @Test
    void aFailureOfThePortPropagates() {
        reader.failure = new IllegalStateException("the store is down");

        assertThrows(IllegalStateException.class,
                () -> service.getBillboard(new BillboardQuery(null, null, 1, 20)));
    }

    private static BillboardEntry entry() {
        return new BillboardEntry(
                new BillboardMovie(MOVIE_ID, "Synthetic Movie", null, 120, List.of(), "PG-13",
                        URI.create("https://example.test/poster.png"), null),
                new BillboardRoom(ROOM_ID, "Room 1", 80),
                new BillboardShowtime(SHOWTIME_ID, Instant.parse("2026-11-01T20:00:00Z"),
                        Instant.parse("2026-11-01T22:00:00Z"), 15000, null, null));
    }

    private static final class FakeReader implements BillboardReader {

        BillboardPage page = new BillboardPage(List.of(), 1, 20, 0);
        RuntimeException failure;
        BillboardQuery query;
        Instant now;

        @Override
        public BillboardPage find(BillboardQuery query, Instant now) {
            this.query = query;
            this.now = now;
            if (failure != null) {
                throw failure;
            }
            return page;
        }
    }
}
