package co.edu.corhuila.csp.catalog.application.port.out;

/** The movie writes that carry an Idempotency-Key (ADR-037). */
public enum MovieOperation {
    CREATE_MOVIE,
    UPDATE_MOVIE,
    ARCHIVE_MOVIE
}
