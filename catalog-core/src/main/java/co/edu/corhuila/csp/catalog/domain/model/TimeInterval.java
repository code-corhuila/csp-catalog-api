package co.edu.corhuila.csp.catalog.domain.model;

import java.time.Instant;

/**
 * The span of a showtime. It is half-open, so a showtime that starts exactly when another ends does not
 * overlap it (ADR-039); a malformed interval throws {@link InvalidShowtimeException}.
 */
public record TimeInterval(Instant start, Instant end) {

    public TimeInterval {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new InvalidShowtimeException("an interval needs a start that is strictly before its end");
        }
    }

    /** Whether both intervals share any instant; touching ends do not count. */
    public boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
