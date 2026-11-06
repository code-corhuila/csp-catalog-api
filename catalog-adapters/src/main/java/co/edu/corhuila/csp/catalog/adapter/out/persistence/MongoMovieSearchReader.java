package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.application.port.in.MovieSearchQuery;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardMovie;
import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchPage;
import co.edu.corhuila.csp.catalog.application.port.out.MovieSearchReader;
import co.edu.corhuila.csp.catalog.domain.model.PublicationStatus;
import com.mongodb.client.MongoCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * The public movie search of ADR-044 read from MongoDB with one query on {@code movies}. The text filter is a
 * {@code $text} query on {@code idx_movies_title_synopsis_text}, the genre filter uses
 * {@code idx_movies_publication_status_genres}, and the maximum duration is applied to the movies they leave.
 * Every word of the text is sent as a quoted phrase, so that all of them are required and none is read as the
 * syntax of the search (a hyphen negates a bare term, a quote opens a phrase). Only letters and digits stay in a
 * phrase, as in the tokens of the text index, so {@code spider-man} asks for {@code "spider"} and {@code "man"}
 * (the database checks a quoted phrase as a substring of the text, so {@code "spider man"} would miss
 * {@code Spider-Man}). The adapter only reads: it creates no collection, index or migration (Norma 5.2.1).
 */
public class MongoMovieSearchReader implements MovieSearchReader {

    private static final String MOVIES = "movies";
    private static final Document ORDER = new Document("title", 1).append("_id", 1);

    private final MongoTemplate template;

    public MongoMovieSearchReader(MongoTemplate template) {
        this.template = template;
    }

    @Override
    public MovieSearchPage find(MovieSearchQuery query) {
        Optional<Document> filter = filter(query);
        if (filter.isEmpty()) {
            return new MovieSearchPage(List.of(), query.page(), query.limit(), 0);
        }
        MongoCollection<Document> movies = template.getCollection(MOVIES);
        List<BillboardMovie> items = movies.find(filter.get()).sort(ORDER)
                .skip((query.page() - 1) * query.limit())
                .limit(query.limit())
                .map(document -> MongoBillboardReader.toPublicMovie(MovieDocumentMapper.toMovie(document)))
                .into(new ArrayList<>());
        return new MovieSearchPage(items, query.page(), query.limit(), movies.countDocuments(filter.get()));
    }

    /** The filter of the query, or empty if the text has words but none can be searched, so nothing matches. */
    private static Optional<Document> filter(MovieSearchQuery query) {
        Document filter = new Document("publication_status", PublicationStatus.PUBLISHED.name())
                .append("deleted_at", null);
        if (query.text() != null) {
            String search = phrases(query.words());
            if (search.isEmpty()) {
                return Optional.empty();
            }
            filter.append("$text", new Document("$search", search));
        }
        if (query.genre() != null) {
            filter.append("genres", query.genre());
        }
        if (query.durationMax() != null) {
            filter.append("duration_minutes", new Document("$lte", query.durationMax()));
        }
        return Optional.of(filter);
    }

    /** Each run of letters and digits as a quoted phrase: any other character only separates tokens. */
    private static String phrases(List<String> words) {
        return words.stream()
                .flatMap(word -> Arrays.stream(word.split("[^\\p{L}\\p{M}\\p{N}]+")))
                .filter(token -> !token.isEmpty())
                .map(token -> "\"" + token + "\"")
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }
}
