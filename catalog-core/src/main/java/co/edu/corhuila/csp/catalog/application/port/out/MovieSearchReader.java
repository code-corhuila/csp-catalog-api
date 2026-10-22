package co.edu.corhuila.csp.catalog.application.port.out;

import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchQuery;

/** Reads the public movie search from the store (ADR-044). */
public interface MovieSearchReader {

    /**
     * The window of the search that answers the query. The contract of ADR-044:
     * <ul>
     *   <li>only movies that are {@code PUBLISHED} and not archived, with or without showtimes;</li>
     *   <li>if the text has words, only movies that have every one of them as a whole word of the title or of
     *       the synopsis, without regard to case or accents;</li>
     *   <li>if {@code genre} is set, only movies that have exactly that genre;</li>
     *   <li>if {@code durationMax} is set, only movies of that duration in minutes or less;</li>
     *   <li>ordered by title ascending and then by movie id;</li>
     *   <li>{@code total} counts the matching movies, not only the ones of the window.</li>
     * </ul>
     */
    MovieSearchPage find(MovieSearchQuery query);
}
