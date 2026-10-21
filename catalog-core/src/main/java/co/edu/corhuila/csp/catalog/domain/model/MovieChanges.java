package co.edu.corhuila.csp.catalog.domain.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** A partial update of a movie: a null field means "unchanged". */
public record MovieChanges(
        String title,
        String synopsis,
        Integer durationMinutes,
        String classification,
        LocalDate releaseDate,
        List<String> genres,
        List<String> cast,
        URI posterUrl,
        PublicationStatus status) {
}
