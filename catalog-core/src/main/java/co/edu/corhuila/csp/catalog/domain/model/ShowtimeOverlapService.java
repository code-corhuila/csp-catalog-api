package co.edu.corhuila.csp.catalog.domain.model;

import java.util.Collection;

/** Domain service of the rule that a room never hosts two scheduled showtimes at once (ADR-039). */
public final class ShowtimeOverlapService {

    private ShowtimeOverlapService() {
    }

    /**
     * Rejects the candidate when another scheduled showtime of its room overlaps it. Cancelled and
     * completed showtimes, and the candidate itself, never conflict.
     */
    public static void requireNoOverlap(Showtime candidate, Collection<Showtime> inTheRoom) {
        inTheRoom.stream()
                .filter(other -> !other.id().equals(candidate.id()))
                .filter(other -> other.roomId().equals(candidate.roomId()))
                .filter(Showtime::isScheduled)
                .filter(other -> other.interval().overlaps(candidate.interval()))
                .findFirst()
                .ifPresent(conflict -> {
                    throw new ShowtimeOverlapException(conflict.id());
                });
    }
}
