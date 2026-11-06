package co.edu.corhuila.csp.catalog.application.usecase;

import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The two steps every idempotent write of the catalog shares: the hash of the request and the replay. */
final class IdempotentWrites {

    private IdempotentWrites() {
    }

    /** SHA-256 of the operation, the resource and the fields of the request, joined with the unit separator. */
    static String requestHash(WriteOperation operation, UUID resourceId, Object... fields) {
        String canonical = Stream.concat(Stream.of(operation, resourceId), Stream.of(fields))
                .map(String::valueOf)
                .collect(Collectors.joining("\u001f"));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java platform", e);
        }
    }

    /** A replay answers with the stored resource; otherwise the write runs. */
    static <T> T replayOr(Optional<UUID> replayed, Function<UUID, T> load, Supplier<T> write) {
        return replayed.isPresent() ? load.apply(replayed.get()) : write.get();
    }
}
