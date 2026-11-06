package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The service answers over HTTPS on its single port, with the certificate of the platform, and not over plain HTTP (ADR-067). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpsOnlyTest extends ApplicationContextTest {

    private static final String HEALTH = "/api/v1/catalog/health";

    @LocalServerPort
    private int port;

    @Test
    void theServiceAnswersOverHttpsToACallerThatTrustsItsCertificate() throws Exception {
        HttpResponse<String> response = trusting().send(request("https"), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }

    @Test
    void theServiceDoesNotAnswerOverPlainHttpOnTheSamePort() throws Exception {
        boolean answered;
        try {
            answered = HttpClient.newHttpClient().send(request("http"), HttpResponse.BodyHandlers.ofString())
                    .statusCode() == 200;
        } catch (IOException refused) {
            answered = false;
        }

        assertFalse(answered);
    }

    @Test
    void aCallerThatDoesNotTrustTheCertificateFailsTheHandshake() {
        assertThrows(SSLHandshakeException.class,
                () -> HttpClient.newHttpClient().send(request("https"), HttpResponse.BodyHandlers.ofString()));
    }

    private HttpRequest request(String scheme) {
        return HttpRequest.newBuilder(URI.create(scheme + "://localhost:" + port + HEALTH)).GET().build();
    }

    private HttpClient trusting() throws Exception {
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(TlsTestFiles.shared().trustStore());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trust.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(context).build();
    }
}
