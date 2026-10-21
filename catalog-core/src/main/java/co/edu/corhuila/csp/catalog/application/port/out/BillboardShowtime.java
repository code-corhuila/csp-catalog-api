package co.edu.corhuila.csp.catalog.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** The public data of a scheduled showtime; its movie and room travel in the {@link BillboardEntry}. */
public record BillboardShowtime(
        UUID id,
        Instant startsAt,
        Instant endsAt,
        long priceCents,
        String audioLanguage,
        String subtitleLanguage) {
}
