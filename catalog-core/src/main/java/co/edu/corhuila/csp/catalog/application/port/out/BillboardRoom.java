package co.edu.corhuila.csp.catalog.application.port.out;

import java.util.UUID;

/** The public data of a room: no seat layout (ADR-041). */
public record BillboardRoom(UUID id, String name, int capacity) {
}
