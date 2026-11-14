package co.edu.corhuila.csp.catalog.application.port.out;

/** The writes of the catalog that carry an Idempotency-Key (ADR-037, ADR-038, ADR-039). */
public enum WriteOperation {
    CREATE_MOVIE,
    UPDATE_MOVIE,
    ARCHIVE_MOVIE,
    CREATE_ROOM,
    UPDATE_ROOM,
    ARCHIVE_ROOM,
    CREATE_SHOWTIME,
    UPDATE_SHOWTIME,
    CANCEL_SHOWTIME
}
