package co.edu.corhuila.csp.catalog.application.port.out;

/**
 * The same Idempotency-Key arrived with another request. The contract answers 409 instead of
 * guessing which of the two requests the client means (Norma 5.3.8).
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("Idempotency key has already been used with a different payload");
    }
}
