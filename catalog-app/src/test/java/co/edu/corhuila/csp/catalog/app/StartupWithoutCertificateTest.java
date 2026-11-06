package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Catalog does not start without the certificate and the key it serves HTTPS with: it fails closed (ADR-067). */
class StartupWithoutCertificateTest {

    @Test
    void withoutTheCertificateFileTheServiceDoesNotStart() {
        Exception failure = assertThrows(Exception.class, () -> start(
                "--CATALOG_TLS_PRIVATE_KEY_FILE=" + TlsTestFiles.shared().privateKeyFile()));

        assertTrue(messages(failure).contains("CATALOG_TLS_CERTIFICATE_FILE"), messages(failure));
    }

    @Test
    void withoutThePrivateKeyFileTheServiceDoesNotStart() {
        Exception failure = assertThrows(Exception.class, () -> start(
                "--CATALOG_TLS_CERTIFICATE_FILE=" + TlsTestFiles.shared().certificateFile()));

        assertTrue(messages(failure).contains("CATALOG_TLS_PRIVATE_KEY_FILE"), messages(failure));
    }

    @Test
    void withACertificateFileThatDoesNotExistTheServiceDoesNotStart() {
        assertThrows(Exception.class, () -> start(
                "--CATALOG_TLS_CERTIFICATE_FILE=/missing/tls.crt",
                "--CATALOG_TLS_PRIVATE_KEY_FILE=" + TlsTestFiles.shared().privateKeyFile()));
    }

    private void start(String... tlsArguments) {
        String[] arguments = new String[tlsArguments.length + 2];
        arguments[0] = "--server.port=0";
        arguments[1] = "--jwt.public-key=" + RsaTestKey.publicPem().replace("\n", "\\n");
        System.arraycopy(tlsArguments, 0, arguments, 2, tlsArguments.length);
        new SpringApplicationBuilder(CatalogApplication.class).web(WebApplicationType.SERVLET).run(arguments).close();
    }

    private String messages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            all.append(cause.getMessage()).append('\n');
        }
        return all.toString();
    }
}
