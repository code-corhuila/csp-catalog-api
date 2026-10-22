package co.edu.corhuila.csp.catalog.application.usecase;

import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchQuery;
import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchPage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchReader;

/** The movie search use case (ADR-044). The query is already validated; the selection is left to the reader. */
public class MovieSearchService implements MovieSearchUseCases {

    private final MovieSearchReader reader;

    public MovieSearchService(MovieSearchReader reader) {
        this.reader = reader;
    }

    @Override
    public MovieSearchPage search(MovieSearchQuery query) {
        return reader.find(query);
    }
}
