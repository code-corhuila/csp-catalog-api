package co.edu.corhuila.csp.catalog.app;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base of the tests that start the application: the service refuses to start without a public key (Norma 5.3.7)
 * and without the certificate and key it serves HTTPS with (ADR-067).
 */
abstract class ApplicationContextTest {

    @DynamicPropertySource
    static void jwtPublicKey(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", RsaTestKey::publicPem);
    }

    @DynamicPropertySource
    static void tlsCertificate(DynamicPropertyRegistry registry) {
        registry.add("CATALOG_TLS_CERTIFICATE_FILE", () -> TlsTestFiles.shared().certificateFile());
        registry.add("CATALOG_TLS_PRIVATE_KEY_FILE", () -> TlsTestFiles.shared().privateKeyFile());
    }
}
