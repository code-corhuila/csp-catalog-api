package co.edu.corhuila.csp.catalog.adapter.in.http;

import java.util.Set;

/**
 * The {@code sub} of the service tokens that may read the availability of a showtime (ADR-042 A1). The set is
 * decided by the composition root from the environment; an empty set allows nobody.
 */
public record ServiceSubjects(Set<String> values) {

    public ServiceSubjects {
        values = Set.copyOf(values);
    }

    public boolean contains(String subject) {
        return subject != null && values.contains(subject);
    }
}
