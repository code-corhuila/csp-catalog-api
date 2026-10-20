package co.edu.corhuila.csp.catalog.app;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base of the tests that start the application: the service refuses to start without a public key. */
abstract class ApplicationContextTest {

    @DynamicPropertySource
    static void jwtPublicKey(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", RsaTestKey::publicPem);
    }
}
