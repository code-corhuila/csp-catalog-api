package co.edu.corhuila.csp.catalog.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;

/**
 * A throwaway server certificate for the run, generated with the {@code keytool} of the JDK that runs the tests and
 * written as the two PEM files the platform mounts. Nothing is kept in the repository: no key, no certificate.
 */
final class TlsTestFiles {

    private static final String PASSWORD = "throwaway";
    private static final TlsTestFiles INSTANCE = generate();

    private final Path directory;
    private final Certificate certificate;

    private TlsTestFiles(Path directory, Certificate certificate) {
        this.directory = directory;
        this.certificate = certificate;
    }

    static TlsTestFiles shared() {
        return INSTANCE;
    }

    String certificateFile() {
        return directory.resolve("tls.crt").toString().replace('\\', '/');
    }

    String privateKeyFile() {
        return directory.resolve("tls.key").toString().replace('\\', '/');
    }

    /** A trust store with this certificate only, to call the service the way a caller that trusts its CA does. */
    KeyStore trustStore() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("catalog-api", certificate);
        return store;
    }

    private static TlsTestFiles generate() {
        try {
            Path directory = Files.createTempDirectory("catalog-tls");
            directory.toFile().deleteOnExit();
            Path store = directory.resolve("server.p12");
            run(directory, System.getProperty("java.home") + "/bin/keytool", "-genkeypair", "-alias", "catalog-api",
                    "-keyalg", "RSA", "-keysize", "2048", "-validity", "1", "-dname", "CN=catalog-api",
                    "-ext", "san=dns:catalog-api,dns:localhost", "-storetype", "PKCS12", "-keystore", store.toString(),
                    "-storepass", PASSWORD, "-keypass", PASSWORD);
            KeyStore keys = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(store)) {
                keys.load(in, PASSWORD.toCharArray());
            }
            Certificate certificate = keys.getCertificate("catalog-api");
            PrivateKey key = (PrivateKey) keys.getKey("catalog-api", PASSWORD.toCharArray());
            write(directory.resolve("tls.crt"), "CERTIFICATE", certificate.getEncoded());
            write(directory.resolve("tls.key"), "PRIVATE KEY", key.getEncoded());
            return new TlsTestFiles(directory, certificate);
        } catch (Exception exception) {
            throw new IllegalStateException("the throwaway certificate of the tests could not be generated", exception);
        }
    }

    private static void run(Path directory, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }
    }

    private static void write(Path file, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        Files.writeString(file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }
}
