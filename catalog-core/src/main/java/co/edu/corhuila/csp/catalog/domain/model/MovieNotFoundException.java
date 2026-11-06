package co.edu.corhuila.csp.catalog.domain.model;

import java.util.UUID;

/** No movie has that id. */
public class MovieNotFoundException extends DomainException {

    public MovieNotFoundException(UUID movieId) {
        super("Movie " + movieId + " was not found");
    }
}
