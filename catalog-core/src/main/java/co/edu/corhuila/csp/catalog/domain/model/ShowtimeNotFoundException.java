package co.edu.corhuila.csp.catalog.domain.model;

import java.util.UUID;

/** No showtime has that id. */
public class ShowtimeNotFoundException extends DomainException {

    public ShowtimeNotFoundException(UUID showtimeId) {
        super("Showtime " + showtimeId + " was not found");
    }
}
