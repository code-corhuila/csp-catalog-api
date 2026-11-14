package co.edu.corhuila.csp.catalog.domain.model;

import java.util.UUID;

/** A scheduled showtime already occupies the room during that interval (HU-CATALOG-003, ADR-039). */
public class ShowtimeOverlapException extends DomainException {

    public ShowtimeOverlapException(UUID conflictingShowtimeId) {
        super("The room is already occupied by showtime " + conflictingShowtimeId + " during that interval");
    }
}
