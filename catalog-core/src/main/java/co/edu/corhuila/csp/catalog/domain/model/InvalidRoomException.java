package co.edu.corhuila.csp.catalog.domain.model;

/**
 * A room was built from values that no valid room can have, for example no seats or a repeated seat
 * label (HU-CATALOG-002). The HTTP adapter answers it as a validation error.
 */
public class InvalidRoomException extends DomainException {

    public InvalidRoomException(String message) {
        super(message);
    }
}
