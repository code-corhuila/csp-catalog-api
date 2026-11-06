package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.ShowtimeChanges;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import java.time.Instant;

/** The body of {@code PATCH /showtimes/{showtimeId}} ({@code UpdateShowtimeRequest}): a field that is not sent is unchanged. */
public record UpdateShowtimeRequest(
        Instant startsAt,
        Instant endsAt,
        Long priceCents,
        String audioLanguage,
        String subtitleLanguage,
        ShowtimeStatus status) {

    ShowtimeChanges changes() {
        return new ShowtimeChanges(startsAt, endsAt, priceCents, audioLanguage, subtitleLanguage, status);
    }
}
