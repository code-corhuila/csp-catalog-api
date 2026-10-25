package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code Showtime} of the contract: the id, the fields of the creation request and the status. The snapshots
 * of the movie title and the room name are not part of the contract and stay in the domain. An optional field
 * that has no value is left out, because the contract does not declare them nullable. The entity never leaves
 * the adapter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShowtimeResponse(
        UUID id,
        UUID movieId,
        UUID roomId,
        Instant startsAt,
        Instant endsAt,
        long priceCents,
        String audioLanguage,
        String subtitleLanguage,
        String status) {

    static ShowtimeResponse of(Showtime showtime) {
        return new ShowtimeResponse(showtime.id(), showtime.movieId(), showtime.roomId(),
                showtime.interval().start(), showtime.interval().end(), showtime.priceCents(),
                showtime.audioLanguage(), showtime.subtitleLanguage(), showtime.status().name());
    }
}
