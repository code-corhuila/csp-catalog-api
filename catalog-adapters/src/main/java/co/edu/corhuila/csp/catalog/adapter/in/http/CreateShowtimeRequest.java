package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.in.CreateShowtimeInput;
import co.edu.corhuila.csp.catalog.domain.model.InvalidShowtimeException;
import java.time.Instant;
import java.util.UUID;

/**
 * The body of {@code POST /showtimes} ({@code CreateShowtimeRequest} of the contract). A field the contract
 * requires and the client left out is a 400 before the use case runs; the other invariants (interval, price,
 * languages) are judged by the domain.
 */
public record CreateShowtimeRequest(
        UUID movieId,
        UUID roomId,
        Instant startsAt,
        Instant endsAt,
        Long priceCents,
        String audioLanguage,
        String subtitleLanguage) {

    CreateShowtimeInput input(String idempotencyKey) {
        return new CreateShowtimeInput(idempotencyKey, required(movieId, "movieId"), required(roomId, "roomId"),
                required(startsAt, "startsAt"), required(endsAt, "endsAt"), required(priceCents, "priceCents"),
                audioLanguage, subtitleLanguage);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new InvalidShowtimeException("the field " + field + " is required");
        }
        return value;
    }
}
