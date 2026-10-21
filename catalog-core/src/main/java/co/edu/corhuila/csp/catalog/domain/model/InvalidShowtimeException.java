package co.edu.corhuila.csp.catalog.domain.model;

/**
 * A showtime was built from values that no valid showtime can have, for example a negative price or an
 * interval that ends before it starts (HU-CATALOG-003). The HTTP adapter answers it as a validation error.
 */
public class InvalidShowtimeException extends DomainException {

    public InvalidShowtimeException(String message) {
        super(message);
    }
}
