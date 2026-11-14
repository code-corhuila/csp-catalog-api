package co.edu.corhuila.csp.catalog.application.port.in;

import java.util.List;

/**
 * The filters and the window of a movie search (ADR-044). Every filter is optional: {@code null} means no filter.
 * A blank text is ignored; a blank or too long genre, a maximum duration below one, a page below one or a limit
 * outside 1 to {@value #MAX_LIMIT} is malformed.
 */
public record MovieSearchQuery(String text, String genre, Integer durationMax, int page, int limit) {

    public static final int MAX_LIMIT = 100;
    public static final int MAX_TEXT_LENGTH = 100;
    public static final int MAX_GENRE_LENGTH = 50;

    public MovieSearchQuery {
        text = normalized(text);
        if (genre != null && (genre.isBlank() || genre.length() > MAX_GENRE_LENGTH)) {
            throw new IllegalArgumentException("genre is not blank and has at most " + MAX_GENRE_LENGTH + " characters");
        }
        if (durationMax != null && durationMax < 1) {
            throw new IllegalArgumentException("durationMax starts at 1");
        }
        if (page < 1 || limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("page starts at 1 and limit is between 1 and " + MAX_LIMIT);
        }
    }

    /** The words of the text: the runs of non-blank characters. Empty if there is no text. */
    public List<String> words() {
        return text == null ? List.of() : List.of(text.split("\\s+"));
    }

    private static String normalized(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("text has at most " + MAX_TEXT_LENGTH + " characters");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
