package co.edu.corhuila.csp.catalog.app;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/** A key pair generated for the test run: the public half configures the service, the private half signs tokens. */
final class RsaTestKey {

    private static final KeyPair KEYS = generate();

    private RsaTestKey() {
    }

    static String publicPem() {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(KEYS.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    static String token(List<String> permissions) {
        return token("11111111-1111-4111-8111-111111111111", permissions);
    }

    static String token(String subject, List<String> permissions) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .claim("permissions", permissions)
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
