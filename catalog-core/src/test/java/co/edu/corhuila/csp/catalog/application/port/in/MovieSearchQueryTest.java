package co.edu.corhuila.csp.catalog.application.port.in;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The filters and the window of a movie search (ADR-044). */
class MovieSearchQueryTest {

    @Test
    void theFiltersAreOptional() {
        MovieSearchQuery query = new MovieSearchQuery(null, null, null, 1, 20);

        assertNull(query.text());
        assertNull(query.genre());
        assertNull(query.durationMax());
        assertTrue(query.words().isEmpty());
    }

    @Test
    void theFiltersAreKept() {
        MovieSearchQuery query = new MovieSearchQuery("space action", "Ciencia ficción", 150, 2, 10);

        assertEquals("space action", query.text());
        assertEquals("Ciencia ficción", query.genre());
        assertEquals(150, query.durationMax());
    }

    @Test
    void theTextIsTrimmed() {
        assertEquals("space", new MovieSearchQuery("  space \t", null, null, 1, 20).text());
    }

    @Test
    void aBlankTextIsIgnored() {
        assertNull(new MovieSearchQuery("", null, null, 1, 20).text());
        assertNull(new MovieSearchQuery("   \t ", null, null, 1, 20).text());
    }

    @Test
    void theTextHasAtMostOneHundredCharactersAfterTrimming() {
        assertDoesNotThrow(() -> new MovieSearchQuery("x".repeat(100), null, null, 1, 20));
        assertDoesNotThrow(() -> new MovieSearchQuery("  " + "x".repeat(100) + "  ", null, null, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> new MovieSearchQuery("x".repeat(101), null, null, 1, 20));
    }

    @Test
    void theWordsAreTheRunsOfNonBlankCharactersOfTheText() {
        MovieSearchQuery query = new MovieSearchQuery("  space   galaxy\tquest ", null, null, 1, 20);

        assertEquals(List.of("space", "galaxy", "quest"), query.words());
    }

    @Test
    void theGenreIsNotBlankAndHasAtMostFiftyCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new MovieSearchQuery(null, "", null, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> new MovieSearchQuery(null, "   ", null, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> new MovieSearchQuery(null, "g".repeat(51), null, 1, 20));
        assertDoesNotThrow(() -> new MovieSearchQuery(null, "g".repeat(50), null, 1, 20));
    }

    @Test
    void theGenreKeepsItsCaseAndAccents() {
        assertEquals("Acción", new MovieSearchQuery(null, "Acción", null, 1, 20).genre());
    }

    @Test
    void theMaximumDurationStartsAtOne() {
        assertThrows(IllegalArgumentException.class, () -> new MovieSearchQuery(null, null, 0, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> new MovieSearchQuery(null, null, -5, 1, 20));
        assertDoesNotThrow(() -> new MovieSearchQuery(null, null, 1, 1, 20));
    }

    @Test
    void thePageStartsAtOne() {
        assertThrows(IllegalArgumentException.class, () -> new MovieSearchQuery(null, null, null, 0, 20));
        assertDoesNotThrow(() -> new MovieSearchQuery(null, null, null, 1, 20));
    }

    @Test
    void theLimitIsBetweenOneAndOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> new MovieSearchQuery(null, null, null, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MovieSearchQuery(null, null, null, 1, 101));
        assertDoesNotThrow(() -> new MovieSearchQuery(null, null, null, 1, 1));
        assertDoesNotThrow(() -> new MovieSearchQuery(null, null, null, 1, 100));
    }
}
