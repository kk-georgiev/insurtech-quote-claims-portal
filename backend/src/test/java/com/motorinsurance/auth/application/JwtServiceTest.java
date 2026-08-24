package com.motorinsurance.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motorinsurance.auth.domain.Role;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * Focused unit coverage for {@link JwtService#parseToken} (Story 1.4) - a
 * plain JUnit test with no Spring context, so it runs fast and every "not a
 * valid token" failure mode is exercised and localized directly, rather than
 * only indirectly over full HTTP as in {@code JwtAuthenticationFilterTest}.
 *
 * <p>Every negative case here asserts {@code parseToken} throws {@link
 * JwtException} or {@link IllegalArgumentException} - the two types {@code
 * JwtAuthenticationFilter} catches (see its javadoc) - and never anything
 * else uncaught, which is exactly the bug class this test guards against
 * (e.g. a missing {@code sub} claim previously reached {@code
 * UUID.fromString(null)} and threw an uncaught {@code
 * NullPointerException}).
 *
 * <p>Tokens for the negative cases are hand-built with the same JJWT builder
 * {@link JwtService#issueToken} itself uses, deliberately omitting or
 * corrupting one claim at a time - {@code JwtService} has no seam to inject
 * partial claims through its own API, so this duplicates the signing-key
 * construction rather than reusing {@code JwtService}'s private key
 * (tracked as a deferred cleanup, not blocking this story).
 */
class JwtServiceTest {

    // Long enough for HS256 (>=256 bits) - the same constraint JwtService's
    // own constructor enforces via Keys.hmacShaKeyFor.
    private static final String SECRET = "test-only-jwt-secret-at-least-32-bytes-long-for-hs256-signing";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtService jwtService = new JwtService(SECRET, 8);

    @Test
    void parseToken_roundTripsAnIssuedToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.issueToken(userId, Role.CLIENT);
        JwtService.ParsedToken parsed = jwtService.parseToken(token);

        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.role()).isEqualTo(Role.CLIENT);
    }

    @Test
    void parseToken_missingSubjectClaim_throws() {
        String token = Jwts.builder()
                .claim("role", Role.CLIENT.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SIGNING_KEY, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    void parseToken_missingExpirationClaim_throws() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", Role.CLIENT.name())
                .issuedAt(Date.from(Instant.now()))
                .signWith(SIGNING_KEY, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    void parseToken_missingRoleClaim_throws() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SIGNING_KEY, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    void parseToken_roleClaimNotARealRoleConstant_throws() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "NOT_A_REAL_ROLE")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SIGNING_KEY, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    void parseToken_subjectNotAValidUuid_throws() {
        String token = Jwts.builder()
                .subject("not-a-uuid")
                .claim("role", Role.CLIENT.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SIGNING_KEY, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    void parseToken_signedWithADifferentKey_throws() {
        // Otherwise well-formed and complete - only the signature is wrong -
        // distinct from a syntactically malformed string, which fails
        // earlier in JJWT's own parsing before signature verification even
        // runs.
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "a-completely-different-jwt-secret-also-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", Role.CLIENT.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }
}
