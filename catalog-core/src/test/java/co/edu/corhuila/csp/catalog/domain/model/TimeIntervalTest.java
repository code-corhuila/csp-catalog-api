package co.edu.corhuila.csp.catalog.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The invariants of the time interval and its half-open overlap rule (HU-CATALOG-003, ADR-039). */
class TimeIntervalTest {

    private static final Instant NINE = Instant.parse("2026-11-01T21:00:00Z");

    private static TimeInterval interval(String start, String end) {
        return new TimeInterval(Instant.parse(start), Instant.parse(end));
    }

    @Test
    void aValidIntervalKeepsItsBounds() {
        TimeInterval interval = interval("2026-11-01T20:00:00Z", "2026-11-01T22:00:00Z");

        assertEquals(Instant.parse("2026-11-01T20:00:00Z"), interval.start());
        assertEquals(Instant.parse("2026-11-01T22:00:00Z"), interval.end());
    }

    @Test
    void anEndEqualToTheStartIsRejected() {
        assertThrows(InvalidShowtimeException.class, () -> new TimeInterval(NINE, NINE));
    }

    @Test
    void anEndBeforeTheStartIsRejected() {
        assertThrows(InvalidShowtimeException.class, () -> new TimeInterval(NINE, NINE.minusSeconds(1)));
    }

    @Test
    void aMissingBoundIsRejected() {
        assertThrows(InvalidShowtimeException.class, () -> new TimeInterval(null, NINE));
        assertThrows(InvalidShowtimeException.class, () -> new TimeInterval(NINE, null));
    }

    @ParameterizedTest(name = "{0} to {1} against {2} to {3} overlaps: {4}")
    @CsvSource({
            "20:00, 22:00, 21:00, 23:00, true",
            "21:00, 23:00, 20:00, 22:00, true",
            "20:00, 23:00, 21:00, 22:00, true",
            "21:00, 22:00, 20:00, 23:00, true",
            "20:00, 22:00, 20:00, 22:00, true",
            "20:00, 22:00, 22:00, 23:00, false",
            "22:00, 23:00, 20:00, 22:00, false",
            "18:00, 19:00, 20:00, 22:00, false",
            "20:00, 22:00, 18:00, 19:00, false"
    })
    void theOverlapIsHalfOpen(String start, String end, String otherStart, String otherEnd, boolean expected) {
        TimeInterval interval = interval("2026-11-01T" + start + ":00Z", "2026-11-01T" + end + ":00Z");
        TimeInterval other = interval("2026-11-01T" + otherStart + ":00Z", "2026-11-01T" + otherEnd + ":00Z");

        assertEquals(expected, interval.overlaps(other));
    }
}
