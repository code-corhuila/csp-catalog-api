package co.edu.corhuila.csp.catalog.adapter.in.http;

/** The bounds of the {@code Idempotency-Key} header in {@code _shared.yaml}, shared by every write route. */
final class IdempotencyKeys {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    private IdempotencyKeys() {
    }

    /** The key itself when it has 8 to 128 characters; otherwise a 400 through {@link ApiExceptionHandler}. */
    static String checked(String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() < MIN_LENGTH
                || idempotencyKey.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("the Idempotency-Key must have between 8 and 128 characters");
        }
        return idempotencyKey;
    }
}
