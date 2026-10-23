package co.edu.corhuila.csp.catalog.application.usecase;

import co.edu.corhuila.csp.catalog.application.port.in.ArchiveMovieInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateMovieInput;
import co.edu.corhuila.csp.catalog.application.port.in.MovieUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateMovieInput;
import co.edu.corhuila.csp.catalog.application.port.out.IdGenerator;
import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieRepository;
import co.edu.corhuila.csp.catalog.application.port.out.MovieWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import co.edu.corhuila.csp.catalog.domain.model.MovieChanges;
import co.edu.corhuila.csp.catalog.domain.model.MovieDetails;
import co.edu.corhuila.csp.catalog.domain.model.MovieNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The use cases of the movie aggregate. It depends only on the domain and on the ports, and it asks the
 * clock for the time. A write first looks for its key: a replay answers with the stored movie before the
 * rules of the current state are evaluated, so repeating an archive does not fail with an invalid transition.
 */
public class MovieService implements MovieUseCases {

    private final MovieRepository movies;
    private final IdGenerator ids;
    private final Clock clock;

    public MovieService(MovieRepository movies, IdGenerator ids, Clock clock) {
        this.movies = movies;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Movie createMovie(CreateMovieInput input) {
        MovieDetails d = input.details();
        String hash = IdempotentWrites.requestHash(WriteOperation.CREATE_MOVIE, null, d.title(), d.synopsis(),
                d.durationMinutes(), d.classification(), d.releaseDate(), d.genres(), d.cast(), d.posterUrl(),
                input.status());
        return replayOr(input.idempotencyKey(), hash, () -> movies.save(new MovieWrite(
                Movie.create(ids.next(), d, input.status(), now()), WriteOperation.CREATE_MOVIE,
                input.idempotencyKey(), hash)));
    }

    @Override
    public Movie getMovie(UUID movieId) {
        return movies.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
    }

    @Override
    public MoviePage listMovies(int page, int limit) {
        return movies.findActive(page, limit);
    }

    @Override
    public Movie updateMovie(UpdateMovieInput input) {
        MovieChanges c = input.changes();
        String hash = IdempotentWrites.requestHash(WriteOperation.UPDATE_MOVIE, input.movieId(), c.title(),
                c.synopsis(), c.durationMinutes(), c.classification(), c.releaseDate(), c.genres(), c.cast(),
                c.posterUrl(), c.status());
        return replayOr(input.idempotencyKey(), hash, () -> movies.save(new MovieWrite(
                getMovie(input.movieId()).change(c, now()), WriteOperation.UPDATE_MOVIE,
                input.idempotencyKey(), hash)));
    }

    @Override
    public void archiveMovie(ArchiveMovieInput input) {
        String hash = IdempotentWrites.requestHash(WriteOperation.ARCHIVE_MOVIE, input.movieId());
        replayOr(input.idempotencyKey(), hash, () -> movies.save(new MovieWrite(
                getMovie(input.movieId()).archive(now()), WriteOperation.ARCHIVE_MOVIE,
                input.idempotencyKey(), hash)));
    }

    private Movie replayOr(String idempotencyKey, String requestHash, Supplier<Movie> write) {
        return IdempotentWrites.replayOr(movies.findIdempotentResource(idempotencyKey, requestHash), this::getMovie,
                write);
    }

    /** The database keeps milliseconds: a replay must show the same instant the first answer did. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
