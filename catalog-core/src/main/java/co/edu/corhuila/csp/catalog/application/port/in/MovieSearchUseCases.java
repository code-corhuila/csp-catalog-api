package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchPage;

/** What the catalog offers about the public movie search. The HTTP adapter calls this and nothing else. */
public interface MovieSearchUseCases {

    /** The published movies that match the filters of the query, in the window of the query. */
    MovieSearchPage search(MovieSearchQuery query);
}
