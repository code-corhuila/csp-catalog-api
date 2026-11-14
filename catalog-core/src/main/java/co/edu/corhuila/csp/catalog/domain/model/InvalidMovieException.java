package co.edu.corhuila.csp.catalog.domain.model;

/**
 * A movie was built from values that no valid movie can have, for example a duration of zero or a
 * blank title (HU-CATALOG-001, scenario 2). The HTTP adapter answers it as a validation error.
 */
public class InvalidMovieException extends DomainException {

    public InvalidMovieException(String message) {
        super(message);
    }
}
