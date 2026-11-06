package co.edu.corhuila.csp.catalog.domain.model;

import java.time.Instant;

/** The fields of a partial update of a showtime; a {@code null} field is left unchanged. */
public record ShowtimeChanges(
        Instant startsAt,
        Instant endsAt,
        Long priceCents,
        String audioLanguage,
        String subtitleLanguage,
        ShowtimeStatus status) {
}
