package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import co.edu.corhuila.csp.catalog.domain.model.TimeInterval;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** The documents the validators of catalog_db.showtimes and outbox_event expect, and the way back (ADR-039). */
class ShowtimeDocumentMapperTest {

    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MOVIE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ROOM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final Instant STARTS = Instant.parse("2026-10-23T20:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-10-23T22:00:00Z");

    private static Showtime full() {
        return new Showtime(ID, MOVIE_ID, ROOM_ID, "Synthetic Movie", "Synthetic Room", new TimeInterval(STARTS, ENDS),
                15000, "EN", "ES", ShowtimeStatus.SCHEDULED, NOW, NOW);
    }

    private static Showtime bare() {
        return new Showtime(ID, MOVIE_ID, ROOM_ID, "Synthetic Movie", "Synthetic Room", new TimeInterval(STARTS, ENDS),
                0, null, null, ShowtimeStatus.CANCELLED, NOW, NOW.plusSeconds(60));
    }

    @Test
    void theDocumentUsesTheFieldNamesAndTypesOfTheValidator() {
        Document document = ShowtimeDocumentMapper.toDocument(full());

        assertEquals(Set.of("_id", "movie_id", "room_id", "movie_title_snapshot", "room_name_snapshot", "starts_at",
                "ends_at", "price_cents", "audio_language", "subtitle_language", "status", "created_at",
                "updated_at", "deleted_at"), document.keySet());
        assertEquals(ID.toString(), document.get("_id"));
        assertEquals(MOVIE_ID.toString(), document.get("movie_id"));
        assertEquals(ROOM_ID.toString(), document.get("room_id"));
        assertEquals("Synthetic Movie", document.get("movie_title_snapshot"));
        assertEquals("Synthetic Room", document.get("room_name_snapshot"));
        assertEquals(Date.from(STARTS), document.get("starts_at"));
        assertEquals(Date.from(ENDS), document.get("ends_at"));
        assertInstanceOf(Long.class, document.get("price_cents"));
        assertEquals(15000L, document.get("price_cents"));
        assertEquals("EN", document.get("audio_language"));
        assertEquals("ES", document.get("subtitle_language"));
        assertEquals("SCHEDULED", document.get("status"));
        assertEquals(Date.from(NOW), document.get("created_at"));
        assertEquals(Date.from(NOW), document.get("updated_at"));
        assertNull(document.get("deleted_at"));
    }

    @Test
    void aShowtimeSurvivesTheRoundTrip() {
        assertEquals(full(), ShowtimeDocumentMapper.toShowtime(ShowtimeDocumentMapper.toDocument(full())));
    }

    @Test
    void aCancelledShowtimeWithoutLanguagesSurvivesTheRoundTrip() {
        Document document = ShowtimeDocumentMapper.toDocument(bare());

        assertNull(document.get("audio_language"));
        assertNull(document.get("subtitle_language"));
        assertEquals(bare(), ShowtimeDocumentMapper.toShowtime(document));
    }

    @Test
    void aPriceStoredAsAnotherIntegerTypeIsReadBack() {
        Document document = ShowtimeDocumentMapper.toDocument(full()).append("price_cents", 15000);

        assertEquals(15000L, ShowtimeDocumentMapper.toShowtime(document).priceCents());
    }

    @Test
    void theOutboxDocumentCarriesTheEnvelopeFieldsOfTheValidator() {
        Document outbox = ShowtimeDocumentMapper.toOutboxDocument(full().publishedEvent());

        assertEquals(Set.of("_id", "aggregate_type", "aggregate_id", "event_type", "payload", "created_at"),
                outbox.keySet());
        assertEquals(36, outbox.getString("_id").length());
        assertEquals("Showtime", outbox.get("aggregate_type"));
        assertEquals(ID.toString(), outbox.get("aggregate_id"));
        assertEquals("ShowtimePublished", outbox.get("event_type"));
        assertEquals(Date.from(NOW), outbox.get("created_at"));
    }

    @Test
    void theOutboxPayloadFollowsTheEventContract() {
        Document payload = ShowtimeDocumentMapper.toOutboxDocument(full().publishedEvent()).get("payload", Document.class);

        assertEquals(Set.of("showtimeId", "movieId", "roomId", "movieTitle", "roomName", "startsAt", "endsAt",
                "priceCents", "audioLanguage", "subtitleLanguage"), payload.keySet());
        assertEquals(ID.toString(), payload.get("showtimeId"));
        assertEquals(MOVIE_ID.toString(), payload.get("movieId"));
        assertEquals(ROOM_ID.toString(), payload.get("roomId"));
        assertEquals("Synthetic Movie", payload.get("movieTitle"));
        assertEquals("Synthetic Room", payload.get("roomName"));
        assertEquals("2026-10-23T20:00:00Z", payload.get("startsAt"));
        assertEquals("2026-10-23T22:00:00Z", payload.get("endsAt"));
        assertInstanceOf(Long.class, payload.get("priceCents"));
        assertEquals(15000L, payload.get("priceCents"));
        assertEquals("EN", payload.get("audioLanguage"));
        assertEquals("ES", payload.get("subtitleLanguage"));
    }

    @Test
    void theSubtitleLanguageOfThePayloadIsNullWhenAbsentAndEveryEventHasItsOwnId() {
        Showtime noSubtitles = new Showtime(ID, MOVIE_ID, ROOM_ID, "Synthetic Movie", "Synthetic Room",
                new TimeInterval(STARTS, ENDS), 15000, "EN", null, ShowtimeStatus.SCHEDULED, NOW, NOW);

        Document first = ShowtimeDocumentMapper.toOutboxDocument(noSubtitles.publishedEvent());
        Document second = ShowtimeDocumentMapper.toOutboxDocument(noSubtitles.publishedEvent());

        assertNull(first.get("payload", Document.class).get("subtitleLanguage"));
        assertNotEquals(first.get("_id"), second.get("_id"));
    }
}
