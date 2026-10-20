package co.edu.corhuila.csp.catalog.adapter.in.http;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

/** Tokens signed with a key pair generated for the test run: no key is ever committed. */
final class TestTokens {

    static final String SUBJECT = "11111111-1111-4111-8111-111111111111";

    private static final KeyPair KEYS = generate();
    private static final KeyPair OTHER_KEYS = generate();

    private TestTokens() {
    }

    static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEYS.getPublic();
    }

    static JWTClaimsSet.Builder claims(String subject, List<String> permissions, Date expiresAt) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        if (subject != null) {
            builder.subject(subject);
        }
        if (permissions != null) {
            builder.claim("permissions", permissions);
        }
        if (expiresAt != null) {
            builder.expirationTime(expiresAt);
        }
        return builder;
    }

    static String valid(List<String> permissions) {
        return rs256(claims(SUBJECT, permissions, inOneHour()).build(), KEYS);
    }

    static String rs256(JWTClaimsSet claims) {
        return rs256(claims, KEYS);
    }

    static String signedByAnotherKey(List<String> permissions) {
        return rs256(claims(SUBJECT, permissions, inOneHour()).build(), OTHER_KEYS);
    }

    static String hs256(List<String> permissions) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(SUBJECT, permissions, inOneHour()).build());
            jwt.sign(new MACSigner(new byte[32]));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String unsigned(List<String> permissions) {
        return new PlainJWT(claims(SUBJECT, permissions, inOneHour()).build()).serialize();
    }

    static Date inOneHour() {
        return new Date(System.currentTimeMillis() + 3_600_000);
    }

    static Date anHourAgo() {
        return new Date(System.currentTimeMillis() - 3_600_000);
    }

    private static String rs256(JWTClaimsSet claims, KeyPair keys) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
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
