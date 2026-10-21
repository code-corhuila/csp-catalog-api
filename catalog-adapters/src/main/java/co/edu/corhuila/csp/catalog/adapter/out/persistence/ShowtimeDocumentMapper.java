package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimePublished;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import co.edu.corhuila.csp.catalog.domain.model.TimeInterval;
import java.util.Date;
import java.util.UUID;
import org.bson.Document;

/**
 * The showtime and its outbox event as the documents the validators of {@code catalog_db.showtimes} and
 * {@code catalog_db.outbox_event} expect, and the way back. The price is a BSON {@code long} and every date
 * a BSON date, because the validator checks those types. The outbox document holds the payload of the
 * {@code ShowtimePublished} contract; its envelope ({@code eventId}, {@code eventType}, {@code occurredAt},
 * {@code version}) is built by the relay from {@code _id}, {@code event_type}, {@code created_at} and the
 * constant version 1, so none of them is stored apart from those columns.
 */
final class ShowtimeDocumentMapper {

    private static final String AGGREGATE_TYPE = "Showtime";
    private static final String EVENT_TYPE = "ShowtimePublished";

    private ShowtimeDocumentMapper() {
    }

    static Document toDocument(Showtime showtime) {
        return new Document("_id", showtime.id().toString())
                .append("movie_id", showtime.movieId().toString())
                .append("room_id", showtime.roomId().toString())
                .append("movie_title_snapshot", showtime.movieTitleSnapshot())
                .append("room_name_snapshot", showtime.roomNameSnapshot())
                .append("starts_at", Date.from(showtime.interval().start()))
                .append("ends_at", Date.from(showtime.interval().end()))
                .append("price_cents", showtime.priceCents())
                .append("audio_language", showtime.audioLanguage())
                .append("subtitle_language", showtime.subtitleLanguage())
                .append("status", showtime.status().name())
                .append("created_at", Date.from(showtime.createdAt()))
                .append("updated_at", Date.from(showtime.updatedAt()))
                .append("deleted_at", null);
    }

    static Showtime toShowtime(Document document) {
        return new Showtime(
                UUID.fromString(document.getString("_id")),
                UUID.fromString(document.getString("movie_id")),
                UUID.fromString(document.getString("room_id")),
                document.getString("movie_title_snapshot"),
                document.getString("room_name_snapshot"),
                new TimeInterval(document.getDate("starts_at").toInstant(), document.getDate("ends_at").toInstant()),
                document.get("price_cents", Number.class).longValue(),
                document.getString("audio_language"),
                document.getString("subtitle_language"),
                ShowtimeStatus.valueOf(document.getString("status")),
                document.getDate("created_at").toInstant(),
                document.getDate("updated_at").toInstant());
    }

    static Document toOutboxDocument(ShowtimePublished event) {
        Document payload = new Document("showtimeId", event.showtimeId().toString())
                .append("movieId", event.movieId().toString())
                .append("roomId", event.roomId().toString())
                .append("movieTitle", event.movieTitle())
                .append("roomName", event.roomName())
                .append("startsAt", event.startsAt().toString())
                .append("endsAt", event.endsAt().toString())
                .append("priceCents", event.priceCents())
                .append("audioLanguage", event.audioLanguage())
                .append("subtitleLanguage", event.subtitleLanguage());
        return new Document("_id", UUID.randomUUID().toString())
                .append("aggregate_type", AGGREGATE_TYPE)
                .append("aggregate_id", event.showtimeId().toString())
                .append("event_type", EVENT_TYPE)
                .append("payload", payload)
                .append("created_at", Date.from(event.occurredAt()));
    }
}
