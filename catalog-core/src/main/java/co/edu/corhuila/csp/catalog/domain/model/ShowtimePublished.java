package co.edu.corhuila.csp.catalog.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event of a showtime that was scheduled (HU-CATALOG-003). It is a plain record: the adapter that
 * publishes it builds the message envelope.
 */
public record ShowtimePublished(
        UUID showtimeId,
        UUID movieId,
        UUID roomId,
        String movieTitle,
        String roomName,
        Instant startsAt,
        Instant endsAt,
        long priceCents,
        String audioLanguage,
        String subtitleLanguage,
        Instant occurredAt) {
}
