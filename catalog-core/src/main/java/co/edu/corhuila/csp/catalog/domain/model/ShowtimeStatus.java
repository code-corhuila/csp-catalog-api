package co.edu.corhuila.csp.catalog.domain.model;

/**
 * Lifecycle of a showtime (ADR-039): a scheduled showtime is cancelled or completed, and both are final.
 */
public enum ShowtimeStatus {
    SCHEDULED,
    CANCELLED,
    COMPLETED;

    public boolean canTransitionTo(ShowtimeStatus target) {
        return this == SCHEDULED && (target == CANCELLED || target == COMPLETED);
    }
}
