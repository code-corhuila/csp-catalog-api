package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.application.port.out.MoviePage;
import co.edu.corhuila.csp.catalog.domain.model.Movie;
import java.util.UUID;

/** What the catalog offers about movies. The HTTP adapter calls these and nothing else. */
public interface MovieUseCases {

    /**
     * Creates a movie as a draft or already published. A replay of the same key and request returns
     * the stored movie.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException when the
     *         movie is asked to be created archived
     * @throws co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException when
     *         the key was already used with another request
     */
    Movie createMovie(CreateMovieInput input);

    /** The movie with that id, archived or not. */
    Movie getMovie(UUID movieId);

    /** The movies that are not archived, in the window of {@code page} and {@code limit}. */
    MoviePage listMovies(int page, int limit);

    /**
     * Applies a partial update, including a publication.
     *
     * @throws co.edu.corhuila.csp.catalog.domain.model.MovieNotFoundException when no movie has that id
     * @throws co.edu.corhuila.csp.catalog.domain.model.InvalidStatusTransitionException when the
     *         transition is not allowed or the movie is archived
     */
    Movie updateMovie(UpdateMovieInput input);

    /** Archives the movie: it keeps existing, with its deletion date set. */
    void archiveMovie(ArchiveMovieInput input);
}
