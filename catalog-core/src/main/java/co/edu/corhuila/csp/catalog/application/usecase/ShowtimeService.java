package co.edu.corhuila.csp.catalog.application.usecase;

import co.edu.corhuila.csp.catalog.application.port.in.CancelShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.in.ShowtimeUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.out.IdGenerator;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.port.out.RoomRepository;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimePage;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeRepository;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeChanges;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeReferenceException;
import co.edu.corhuila.csp.catalog.domain.model.TimeInterval;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The use cases of the showtime aggregate (ADR-039). It depends only on the domain and on the ports, and it
 * asks the clock for the time. A write first looks for its key: a replay answers before the references or the
 * rules of the current state are evaluated, and a replayed cancel answers without loading the showtime. The
 * overlap with the other showtimes of the room is decided by the repository inside its transaction.
 */
public class ShowtimeService implements ShowtimeUseCases {

    private final ShowtimeRepository showtimes;
    private final MovieRepository movies;
    private final RoomRepository rooms;
    private final IdGenerator ids;
    private final Clock clock;

    public ShowtimeService(ShowtimeRepository showtimes, MovieRepository movies, RoomRepository rooms,
            IdGenerator ids, Clock clock) {
        this.showtimes = showtimes;
        this.movies = movies;
        this.rooms = rooms;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Showtime createShowtime(CreateShowtimeInput input) {
        String hash = IdempotentWrites.requestHash(WriteOperation.CREATE_SHOWTIME, null, input.movieId(),
                input.roomId(), input.startsAt(), input.endsAt(), input.priceCents(), input.audioLanguage(),
                input.subtitleLanguage());
        return replayOr(input.idempotencyKey(), hash, () -> {
            Movie movie = movies.findById(input.movieId())
                    .orElseThrow(() -> new ShowtimeReferenceException("the movie does not exist"));
            Room room = rooms.findById(input.roomId())
                    .orElseThrow(() -> new ShowtimeReferenceException("the room does not exist"));
            Showtime showtime = Showtime.schedule(ids.next(), movie, room,
                    new TimeInterval(input.startsAt(), input.endsAt()), input.priceCents(),
                    input.audioLanguage(), input.subtitleLanguage(), now());
            return showtimes.save(new ShowtimeWrite(showtime, WriteOperation.CREATE_SHOWTIME,
                    input.idempotencyKey(), hash, true, showtime.publishedEvent()));
        });
    }

    @Override
    public Showtime getShowtime(UUID showtimeId) {
        return showtimes.findById(showtimeId).orElseThrow(() -> new ShowtimeNotFoundException(showtimeId));
    }

    @Override
    public ShowtimePage listShowtimes(int page, int limit) {
        return showtimes.findAll(page, limit);
    }

    @Override
    public Showtime updateShowtime(UpdateShowtimeInput input) {
        ShowtimeChanges c = input.changes();
        String hash = IdempotentWrites.requestHash(WriteOperation.UPDATE_SHOWTIME, input.showtimeId(),
                c.startsAt(), c.endsAt(), c.priceCents(), c.audioLanguage(), c.subtitleLanguage(), c.status());
        return replayOr(input.idempotencyKey(), hash, () -> {
            Showtime original = getShowtime(input.showtimeId());
            Showtime changed = original.change(c, now());
            boolean movedInTime = !changed.interval().equals(original.interval());
            return showtimes.save(new ShowtimeWrite(changed, WriteOperation.UPDATE_SHOWTIME,
                    input.idempotencyKey(), hash, changed.isScheduled() && movedInTime, null));
        });
    }

    @Override
    public void cancelShowtime(CancelShowtimeInput input) {
        String hash = IdempotentWrites.requestHash(WriteOperation.CANCEL_SHOWTIME, input.showtimeId());
        IdempotentWrites.replayOr(showtimes.findIdempotentResource(input.idempotencyKey(), hash), id -> null,
                () -> showtimes.save(new ShowtimeWrite(getShowtime(input.showtimeId()).cancel(now()),
                        WriteOperation.CANCEL_SHOWTIME, input.idempotencyKey(), hash, false, null)));
    }

    private Showtime replayOr(String idempotencyKey, String requestHash, Supplier<Showtime> write) {
        return IdempotentWrites.replayOr(showtimes.findIdempotentResource(idempotencyKey, requestHash),
                this::getShowtime, write);
    }

    /** The database keeps milliseconds: a replay must show the same instant the first answer did. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
