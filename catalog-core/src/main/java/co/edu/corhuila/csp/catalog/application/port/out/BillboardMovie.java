package co.edu.corhuila.csp.catalog.application.port.out;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The public data of a published movie: no status, cast or timestamps (ADR-041). */
public record BillboardMovie(
        UUID id,
        String title,
        String synopsis,
        int durationMinutes,
        List<String> genres,
        String classification,
        URI posterUrl,
        LocalDate releaseDate) {
}
