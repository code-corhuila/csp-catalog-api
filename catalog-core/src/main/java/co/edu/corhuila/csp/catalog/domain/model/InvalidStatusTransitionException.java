package co.edu.corhuila.csp.catalog.domain.model;

/** A change of publication status that ADR-037 does not allow, or a change to an archived movie. */
public class InvalidStatusTransitionException extends DomainException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
