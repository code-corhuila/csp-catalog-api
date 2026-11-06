package co.edu.corhuila.csp.catalog.domain.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * What describes a movie. Every invariant of the data model lives here, so no movie with a duration
 * of zero or a blank title can exist; a malformed value throws {@link InvalidMovieException}.
 * It is immutable: {@link #with} returns a new instance, so a failed change touches nothing.
 */
public record MovieDetails(
        String title,
        String synopsis,
        int durationMinutes,
        String classification,
        LocalDate releaseDate,
        List<String> genres,
        List<String> cast,
        URI posterUrl) {

    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_GENRES = 20;
    public static final int MAX_CAST = 50;

    public MovieDetails {
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new InvalidMovieException("the title is required and has at most 255 characters");
        }
        if (durationMinutes < 1) {
            throw new InvalidMovieException("the duration must be at least one minute");
        }
        if (classification == null || classification.isBlank()) {
            throw new InvalidMovieException("the classification is required");
        }
        genres = names(genres, MAX_GENRES, "genres");
        cast = names(cast, MAX_CAST, "cast");
        if (posterUrl != null && !isHttpAddress(posterUrl)) {
            throw new InvalidMovieException("the poster must be an absolute http or https address");
        }
    }

    /** Applies the fields that were sent; the result is validated like any other movie. */
    public MovieDetails with(MovieChanges changes) {
        return new MovieDetails(
                changes.title() != null ? changes.title() : title,
                changes.synopsis() != null ? changes.synopsis() : synopsis,
                changes.durationMinutes() != null ? changes.durationMinutes() : durationMinutes,
                changes.classification() != null ? changes.classification() : classification,
                changes.releaseDate() != null ? changes.releaseDate() : releaseDate,
                changes.genres() != null ? changes.genres() : genres,
                changes.cast() != null ? changes.cast() : cast,
                changes.posterUrl() != null ? changes.posterUrl() : posterUrl);
    }

    private static List<String> names(List<String> values, int max, String what) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > max) {
            throw new InvalidMovieException("at most " + max + " " + what + " are allowed");
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new InvalidMovieException("the " + what + " cannot contain a blank value");
        }
        return List.copyOf(values);
    }

    private static boolean isHttpAddress(URI address) {
        String scheme = address.getScheme();
        return address.getHost() != null && scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
    }
}
