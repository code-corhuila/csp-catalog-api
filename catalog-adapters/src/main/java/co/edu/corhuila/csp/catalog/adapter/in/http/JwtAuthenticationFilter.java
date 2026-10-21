package co.edu.corhuila.csp.catalog.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates and authorizes every request before a controller runs. The service validates the bearer
 * token by itself, as the norm demands (5.3.7): RS256 verified with the public key of the identity service,
 * any other algorithm rejected, {@code exp} and {@code sub} mandatory, {@code sub} the id of a user and
 * {@code permissions} a list of strings. A missing or invalid token is a 401; a valid one without the
 * permission of the route is a 403. Both answer the error envelope and never echo the token.
 *
 * <p>The routes are closed unless a rule opens them: the public ones are those the contract declares with
 * {@code security: []}, every route under {@code /movies}, reads included, needs {@code catalog:write}, and
 * a route with no rule is forbidden, so a route added later cannot be open by omission.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute with the authenticated user: the {@code sub} of the token. */
    public static final String USER_ID_ATTRIBUTE = "userId";

    private static final String BEARER = "Bearer ";
    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/health/ready", "/billboard");
    private static final String MOVIES = "/movies";
    private static final String WRITE_PERMISSION = "catalog:write";

    private final RSAPublicKey publicKey;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(RSAPublicKey jwtPublicKey, ObjectMapper objectMapper) {
        this.publicKey = jwtPublicKey;
        this.objectMapper = objectMapper;
    }

    private record Caller(String userId, List<String> permissions) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // The servlet path and the path info are what the container normalized; the raw request URI is not.
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            reject(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication token required");
            return;
        }
        Optional<Caller> caller = authenticate(header.substring(BEARER.length()).trim());
        if (caller.isEmpty()) {
            reject(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "the token is invalid or expired");
            return;
        }
        if (!isAllowed(path, caller.get().permissions())) {
            reject(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not authorized to perform this action");
            return;
        }
        request.setAttribute(USER_ID_ATTRIBUTE, caller.get().userId());
        chain.doFilter(request, response);
    }

    private static boolean isAllowed(String path, List<String> permissions) {
        boolean movies = path.equals(MOVIES) || path.startsWith(MOVIES + "/");
        return movies && permissions.contains(WRITE_PERMISSION);
    }

    /** The caller of an acceptable token; empty when any rule of 5.3.7 is broken. */
    private Optional<Caller> authenticate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new RSASSAVerifier(publicKey))) {
                logger.debug("rejected token: the algorithm is not RS256 or the signature does not match the key");
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiresAt = claims.getExpirationTime();
            String subject = claims.getSubject();
            if (expiresAt == null || !expiresAt.after(new Date())) {
                logger.debug("rejected token: it has no exp claim or it has expired");
                return Optional.empty();
            }
            if (subject == null || !CorrelationIdFilter.UUID_PATTERN.matcher(subject).matches()) {
                logger.debug("rejected token: its sub is not the id of a user of this platform");
                return Optional.empty();
            }
            List<String> permissions = claims.getStringListClaim("permissions");
            return Optional.of(new Caller(subject, permissions == null ? List.of() : permissions));
        } catch (ParseException | JOSEException exception) {
            logger.debug("rejected token: it is not a verifiable signed JWT");
            return Optional.empty();
        }
    }

    private void reject(HttpServletResponse response, HttpStatus status, String error, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(error, message));
    }
}
