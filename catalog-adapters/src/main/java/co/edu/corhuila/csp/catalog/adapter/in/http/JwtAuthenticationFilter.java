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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
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
 * {@code security: []} (the health checks and {@code GET /billboard}, whatever token it carries), every route under {@code /movies}, reads included, needs {@code catalog:write}, every
 * route under {@code /rooms} needs {@code room:write}, every route under {@code /showtimes} needs
 * {@code showtime:write} except the availability of a showtime, and a route with no rule is forbidden, so a
 * route added later cannot be open by omission.
 *
 * <p>The availability of a showtime ({@code GET /showtimes/{id}/availability}) is the service-to-service route
 * of HU-CATALOG-005 and is governed by the service subjects alone (ADR-042 A1): a valid token whose {@code sub}
 * is one of them opens it, whatever its permissions, and any other valid token is a 403, a user or an
 * administrator with {@code showtime:write} included. The opposite also holds: the token of a service subject
 * opens no other route.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute with the authenticated user: the {@code sub} of the token. */
    public static final String USER_ID_ATTRIBUTE = "userId";

    private static final String BEARER = "Bearer ";
    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/health/ready");
    /** The one public read of the contract besides the health checks: no token is needed and a sent one is ignored. */
    private static final String PUBLIC_BILLBOARD = "/billboard";
    /** The permission that every route under a prefix needs: the routes of a resource and nothing else. */
    private static final Map<String, String> PERMISSION_BY_ROUTE =
            Map.of("/movies", "catalog:write", "/rooms", "room:write", "/showtimes", "showtime:write");
    /** Not served by the showtime routes: the permission of {@code /showtimes} must not open it. */
    private static final Pattern SHOWTIME_AVAILABILITY = Pattern.compile("/showtimes/[^/]+/availability/*");

    private final RSAPublicKey publicKey;
    private final ObjectMapper objectMapper;
    private final ServiceSubjects serviceSubjects;

    public JwtAuthenticationFilter(RSAPublicKey jwtPublicKey, ObjectMapper objectMapper,
            ServiceSubjects serviceSubjects) {
        this.publicKey = jwtPublicKey;
        this.objectMapper = objectMapper;
        this.serviceSubjects = serviceSubjects;
    }

    private record Caller(String subject, List<String> permissions) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // The servlet path and the path info are what the container normalized; the raw request URI is not.
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        if (PUBLIC_PATHS.contains(path) || isPublicBillboardRead(request, path)) {
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
        String subject = caller.get().subject();
        if (isAvailabilityRead(request, path)) {
            if (!serviceSubjects.contains(subject)) {
                reject(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not authorized to perform this action");
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        if (serviceSubjects.contains(subject)) {
            reject(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not authorized to perform this action");
            return;
        }
        if (!CorrelationIdFilter.UUID_PATTERN.matcher(subject).matches()) {
            logger.debug("rejected token: its sub is not the id of a user of this platform");
            reject(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "the token is invalid or expired");
            return;
        }
        if (!isAllowed(path, caller.get().permissions())) {
            reject(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not authorized to perform this action");
            return;
        }
        request.setAttribute(USER_ID_ATTRIBUTE, subject);
        chain.doFilter(request, response);
    }

    private static boolean isAvailabilityRead(HttpServletRequest request, String path) {
        return HttpMethod.GET.matches(request.getMethod()) && SHOWTIME_AVAILABILITY.matcher(path).matches();
    }

    private static boolean isPublicBillboardRead(HttpServletRequest request, String path) {
        return PUBLIC_BILLBOARD.equals(path) && HttpMethod.GET.matches(request.getMethod());
    }

    private static boolean isAllowed(String path, List<String> permissions) {
        return !SHOWTIME_AVAILABILITY.matcher(path).matches() && PERMISSION_BY_ROUTE.entrySet().stream()
                .anyMatch(rule -> isUnder(path, rule.getKey()) && permissions.contains(rule.getValue()));
    }

    private static boolean isUnder(String path, String route) {
        return path.equals(route) || path.startsWith(route + "/");
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
            if (subject == null || subject.isBlank()) {
                logger.debug("rejected token: it has no sub claim");
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
