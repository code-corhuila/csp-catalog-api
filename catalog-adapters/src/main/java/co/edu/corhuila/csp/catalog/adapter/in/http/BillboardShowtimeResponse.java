package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.BillboardShowtime;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code BillboardShowtime} of the contract: the public data of a scheduled showtime. An optional language
 * that has no value is left out, because the contract does not declare it nullable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BillboardShowtimeResponse(
        UUID id,
        Instant startsAt,
        Instant endsAt,
        long priceCents,
        String audioLanguage,
        String subtitleLanguage) {

    static BillboardShowtimeResponse of(BillboardShowtime showtime) {
        return new BillboardShowtimeResponse(showtime.id(), showtime.startsAt(), showtime.endsAt(),
                showtime.priceCents(), showtime.audioLanguage(), showtime.subtitleLanguage());
    }
}
