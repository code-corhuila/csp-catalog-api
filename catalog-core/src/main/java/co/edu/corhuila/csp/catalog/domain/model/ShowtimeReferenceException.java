package co.edu.corhuila.csp.catalog.domain.model;

/** A showtime points to a movie that is not published or to a room that is archived (HU-CATALOG-003). */
public class ShowtimeReferenceException extends DomainException {

    public ShowtimeReferenceException(String message) {
        super(message);
    }
}
