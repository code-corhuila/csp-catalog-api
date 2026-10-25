package co.edu.corhuila.csp.catalog.application.port.out;

/** One line of the billboard: a showtime with the movie and the room it names (ADR-041). */
public record BillboardEntry(BillboardMovie movie, BillboardRoom room, BillboardShowtime showtime) {
}
