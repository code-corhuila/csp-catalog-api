package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The public key of the identity service comes from the environment, and one source is required (Norma 5.3.7). */
class JwtKeyConfigurationTest {

    private final JwtKeyConfiguration configuration = new JwtKeyConfiguration();

    @Test
    void theKeyCanBeTheInlinePem() {
        RSAPublicKey key = configuration.jwtPublicKey(RsaTestKey.publicPem(), "");

        assertEquals("RSA", key.getAlgorithm());
        assertEquals(2048, key.getModulus().bitLength());
    }

    @Test
    void anInlinePemWithEscapedNewlinesIsAccepted() {
        String escaped = RsaTestKey.publicPem().replace("\n", "\\n");

        assertEquals("RSA", configuration.jwtPublicKey(escaped, "").getAlgorithm());
    }

    @Test
    void theKeyCanBeTheFileThePlatformMounts(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("jwt.pub"), RsaTestKey.publicPem());

        assertEquals("RSA", configuration.jwtPublicKey("", file.toString()).getAlgorithm());
    }

    @Test
    void withoutAnySourceTheServiceDoesNotStart() {
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> configuration.jwtPublicKey("", ""));

        assertEquals("jwt.public-key (JWT_PUBLIC_KEY) or jwt.public-key-file (JWT_PUBLIC_KEY_FILE) is required",
                failure.getMessage());
    }

    @Test
    void aFileThatCannotBeReadOrAKeyThatIsNotRsaDoesNotStartTheService(@TempDir Path directory) throws Exception {
        assertThrows(IllegalStateException.class,
                () -> configuration.jwtPublicKey("", directory.resolve("missing.pub").toString()));
        assertThrows(IllegalStateException.class, () -> configuration.jwtPublicKey("not a key", ""));
        Path garbage = Files.writeString(directory.resolve("garbage.pub"),
                "-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----");
        assertThrows(IllegalStateException.class, () -> configuration.jwtPublicKey("", garbage.toString()));
    }
}
