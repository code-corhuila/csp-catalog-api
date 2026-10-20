package co.edu.corhuila.csp.catalog.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A film of the catalog and its publication lifecycle (ADR-037). It is immutable: every transition
 * returns a new instance, so a failed rule changes nothing. Archiving sets {@code deletedAt} and never
 * removes the movie, because showtimes keep referencing it.
 *
 * <p>A malformed movie throws {@link InvalidMovieException} from the constructor; a rule evaluated
 * against the state throws {@link InvalidStatusTransitionException}.
 */
public record Movie(
        UUID id,
        MovieDetails details,
        PublicationStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public Movie {
        if (id == null || details == null || status == null || createdAt == null || updatedAt == null) {
            throw new InvalidMovieException("a movie needs an id, its details, a status and its dates");
        }
    }

    /** A new movie, as a draft or already published; it cannot be created archived. */
    public static Movie create(UUID id, MovieDetails details, PublicationStatus status, Instant now) {
        if (status == PublicationStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("a movie cannot be created archived");
        }
        return new Movie(id, details, status, now, now, null);
    }

    public boolean isArchived() {
        return status == PublicationStatus.ARCHIVED;
    }

    /** Moves to {@code target} when ADR-037 allows it; asking for the current status changes nothing. */
    public Movie withStatus(PublicationStatus target, Instant now) {
        if (status == target) {
            return this;
        }
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("a " + status + " movie cannot become " + target);
        }
        return new Movie(id, details, target, createdAt, now,
                target == PublicationStatus.ARCHIVED ? now : deletedAt);
    }

    public Movie archive(Instant now) {
        if (isArchived()) {
            throw new InvalidStatusTransitionException("the movie is already archived");
        }
        return withStatus(PublicationStatus.ARCHIVED, now);
    }

    /** Applies a partial update; an archived movie is read-only. */
    public Movie change(MovieChanges changes, Instant now) {
        if (isArchived()) {
            throw new InvalidStatusTransitionException("an archived movie cannot be changed");
        }
        Movie changed = new Movie(id, details.with(changes), status, createdAt, now, deletedAt);
        return changes.status() == null ? changed : changed.withStatus(changes.status(), now);
    }
}
