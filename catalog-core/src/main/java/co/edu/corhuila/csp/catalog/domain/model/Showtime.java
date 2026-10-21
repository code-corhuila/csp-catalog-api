package co.edu.corhuila.csp.catalog.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A screening of a movie in a room (HU-CATALOG-003, ADR-039). It is immutable: every change returns a new
 * instance, so a failed rule changes nothing. It keeps the title of the movie and the name of the room
 * as they were when it was scheduled, so a later rename does not rewrite it.
 *
 * <p>A malformed showtime throws {@link InvalidShowtimeException} from the constructor; a rule evaluated
 * against the state throws {@link InvalidStatusTransitionException}.
 */
public record Showtime(
        UUID id,
        UUID movieId,
        UUID roomId,
        String movieTitleSnapshot,
        String roomNameSnapshot,
        TimeInterval interval,
        long priceCents,
        String audioLanguage,
        String subtitleLanguage,
        ShowtimeStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public Showtime {
        if (id == null || movieId == null || roomId == null || interval == null || status == null
                || createdAt == null || updatedAt == null) {
            throw new InvalidShowtimeException(
                    "a showtime needs an id, a movie, a room, an interval, a status and its dates");
        }
        if (isBlank(movieTitleSnapshot) || isBlank(roomNameSnapshot)) {
            throw new InvalidShowtimeException("a showtime needs the title of its movie and the name of its room");
        }
        if (priceCents < 0) {
            throw new InvalidShowtimeException("the price cannot be negative");
        }
        if (isBlankWhenPresent(audioLanguage) || isBlankWhenPresent(subtitleLanguage)) {
            throw new InvalidShowtimeException("a language, when given, cannot be blank");
        }
    }

    /** A new scheduled showtime of a published movie in an active room. */
    public static Showtime schedule(UUID id, Movie movie, Room room, TimeInterval interval, long priceCents,
            String audioLanguage, String subtitleLanguage, Instant now) {
        if (movie.status() != PublicationStatus.PUBLISHED) {
            throw new ShowtimeReferenceException("the movie " + movie.id() + " is not published");
        }
        if (room.isArchived()) {
            throw new ShowtimeReferenceException("the room " + room.id() + " is archived");
        }
        return new Showtime(id, movie.id(), room.id(), movie.details().title(), room.details().name(), interval,
                priceCents, audioLanguage, subtitleLanguage, ShowtimeStatus.SCHEDULED, now, now);
    }

    public boolean isScheduled() {
        return status == ShowtimeStatus.SCHEDULED;
    }

    /** Applies a partial update; only a scheduled showtime can be changed. */
    public Showtime change(ShowtimeChanges changes, Instant now) {
        requireScheduled("changed");
        ShowtimeStatus target = changes.status() != null ? changes.status() : status;
        if (target != status && !status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("a " + status + " showtime cannot become " + target);
        }
        return new Showtime(id, movieId, roomId, movieTitleSnapshot, roomNameSnapshot,
                new TimeInterval(
                        changes.startsAt() != null ? changes.startsAt() : interval.start(),
                        changes.endsAt() != null ? changes.endsAt() : interval.end()),
                changes.priceCents() != null ? changes.priceCents() : priceCents,
                changes.audioLanguage() != null ? changes.audioLanguage() : audioLanguage,
                changes.subtitleLanguage() != null ? changes.subtitleLanguage() : subtitleLanguage,
                target, createdAt, now);
    }

    /** Moves a scheduled showtime to cancelled. */
    public Showtime cancel(Instant now) {
        requireScheduled("cancelled");
        return new Showtime(id, movieId, roomId, movieTitleSnapshot, roomNameSnapshot, interval, priceCents,
                audioLanguage, subtitleLanguage, ShowtimeStatus.CANCELLED, createdAt, now);
    }

    /** The event that announces this showtime; it happened when the showtime was created. */
    public ShowtimePublished publishedEvent() {
        return new ShowtimePublished(id, movieId, roomId, movieTitleSnapshot, roomNameSnapshot, interval.start(),
                interval.end(), priceCents, audioLanguage, subtitleLanguage, createdAt);
    }

    private void requireScheduled(String action) {
        if (!isScheduled()) {
            throw new InvalidStatusTransitionException("a " + status + " showtime cannot be " + action);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isBlankWhenPresent(String value) {
        return value != null && value.isBlank();
    }
}
