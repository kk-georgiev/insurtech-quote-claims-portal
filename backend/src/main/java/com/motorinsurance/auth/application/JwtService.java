package com.motorinsurance.auth.application;

import com.motorinsurance.auth.domain.Role;
import io.jsonwebtoken.Jwts;
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
 * Issues signed JWTs at login (Story 1.3, AD-3/AD-11). Signing only - no
 * token *validation* here; the shared validation filter that reads these
 * tokens back is Story 1.4's job.
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
}
