package co.edu.corhuila.csp.catalog.domain.model;

/**
 * Publication lifecycle of a movie (ADR-037): a draft is published or archived, a published movie is
 * archived, and an archived movie is final.
 */
public enum PublicationStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean canTransitionTo(PublicationStatus target) {
        return switch (this) {
            case DRAFT -> target == PUBLISHED || target == ARCHIVED;
            case PUBLISHED -> target == ARCHIVED;
            case ARCHIVED -> false;
        };
    }
}
