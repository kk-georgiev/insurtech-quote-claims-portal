package com.motorinsurance.auth.application;

import com.motorinsurance.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and validates signed JWTs (Story 1.3 issuance, Story 1.4
 * validation, AD-3/AD-11) - the single source of truth for both directions,
 * so the shared {@code JwtAuthenticationFilter} never parses/verifies a
 * token itself.
 *
 * <p>Signed HS256 with a single symmetric key read from {@code jwt.secret}
 * (ultimately the {@code JWT_SECRET} env var, see {@code application.yml}
 * and {@code .env.example}). Claims are exactly {@code sub} (user id, as a
 * string) and {@code role} (the {@link Role} enum name) plus the standard
 * {@code iat}/{@code exp} - no more, per Story 1.3 Boundaries &amp;
 * Constraints.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationHours;

    public JwtService(
            @Value("${jwt.secret}") String secret, @Value("${jwt.expiration-hours}") long expirationHours) {
        // Explicit HS256 below (not left to a "strongest algorithm the key
        // affords" default) is what actually pins the algorithm - this key
        // wrapping only needs to produce *a* valid HMAC key of sufficient
        // length; hmacShaKeyFor throws WeakKeyException if the configured
        // secret is under 256 bits, which is the fail-fast behavior we want.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    /** Issues a signed token carrying {@code userId} and {@code role}, expiring in {@code jwt.expiration-hours}. */
    public String issueToken(UUID userId, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationHours, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validates {@code token}'s signature and expiry and extracts the user
     * id and {@link Role} it carries (Story 1.4). Throws {@link JwtException}
     * (or a subtype - covers a bad signature, malformed token, and expired
     * {@code exp}) or {@link IllegalArgumentException} (blank/null input) for
     * anything that doesn't parse to a valid, currently-live token issued by
     * {@link #issueToken}. Callers - just {@code JwtAuthenticationFilter} -
     * treat any such failure identically: leave the security context empty
     * and let the request fall through to the 401 entry point.
     */
    public ParsedToken parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String roleClaim = claims.get("role", String.class);
        if (roleClaim == null) {
            throw new MalformedJwtException("Token is missing the required 'role' claim");
        }

        // getSubject()/getExpiration() return null rather than throwing when
        // the claim is absent - sub and exp are both OPTIONAL per RFC 7519,
        // so JJWT enforces neither on its own (in particular, a token with
        // no exp claim is treated as never-expiring, silently defeating the
        // multi-hour-expiry invariant, AD-3). Both are required here
        // explicitly, the same way the role claim is just above - a null
        // subject would otherwise reach UUID.fromString(null) and throw
        // NullPointerException, a type JwtAuthenticationFilter's
        // catch (JwtException | IllegalArgumentException) does not catch,
        // so it would propagate unhandled instead of collapsing to the
        // documented uniform 401.
        String subjectClaim = claims.getSubject();
        if (subjectClaim == null) {
            throw new MalformedJwtException("Token is missing the required 'sub' claim");
        }
        if (claims.getExpiration() == null) {
            throw new MalformedJwtException("Token is missing the required 'exp' claim");
        }

        UUID userId;
        try {
            userId = UUID.fromString(subjectClaim);
        } catch (IllegalArgumentException ex) {
            throw new MalformedJwtException("Token subject is not a valid user id", ex);
        }

        Role role;
        try {
            role = Role.valueOf(roleClaim);
        } catch (IllegalArgumentException ex) {
            throw new MalformedJwtException("Token 'role' claim is not a recognized role", ex);
        }

        return new ParsedToken(userId, role);
    }

    /** The user id and {@link Role} extracted from a validated token (see {@link #parseToken}). */
    public record ParsedToken(UUID userId, Role role) {
    }
}
