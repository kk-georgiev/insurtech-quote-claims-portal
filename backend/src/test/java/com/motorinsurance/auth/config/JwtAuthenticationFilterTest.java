package com.motorinsurance.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.motorinsurance.auth.application.JwtService;
import com.motorinsurance.auth.domain.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Proves the shared JWT authentication gate added by Story 1.4
 * ({@link JwtAuthenticationFilter} + {@link SecurityConfig}) against a
 * throwaway, test-only controller - see the story spec's Design Notes,
 * "Why a test-only controller": Story 1.5 creates the first real protected
 * endpoint and just annotates it the same way {@link TestProtectedController}
 * does here; nothing in this story's mechanism needs to change for that.
 *
 * <p>Also proves the existing public endpoints (Story 1.1's health check,
 * Story 1.2/1.3's register/login) still work with no token at all - zero
 * regression from adding {@code spring-boot-starter-security} to the
 * classpath.
 *
 * <p>Uses {@link RestClient} directly (bound to the running server's random
 * port) rather than {@code TestRestTemplate} - Spring Boot 4 / Spring
 * Framework 7 dropped {@code TestRestTemplate} in favor of
 * {@code RestClient}/{@code RestTestClient}; {@code RestClient}'s
 * {@code exchange(...)} form is used throughout so a non-2xx response is
 * returned to assert on, not thrown as an exception.
 *
 * <p>{@link TestProtectedController} is registered via {@code @Import}
 * rather than relying on {@code @SpringBootApplication}'s component scan to
 * find it: Maven Surefire's test classpath keeps {@code target/classes} and
 * {@code target/test-classes} as separate roots, and package-based
 * classpath scanning here only walked the former, so a test-only component
 * living under {@code src/test/java} was never a candidate - {@code @Import}
 * registers it explicitly and sidesteps that entirely.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(JwtAuthenticationFilterTest.TestProtectedController.class)
class JwtAuthenticationFilterTest {

    private static final String PROTECTED_PATH = "/api/v1/_test/protected";
    private static final String CLIENT_ONLY_PATH = "/api/v1/_test/client-only";

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    // Same property JwtService itself signs/verifies with (see
    // application.yml) - read here directly (rather than reusing
    // JwtService's private key) purely to forge one deliberately-expired
    // token for the expired-token test case below.
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void noToken_onProtectedEndpoint_isRejectedUnauthenticated() {
        ResponseEntity<String> response = client().get().uri(PROTECTED_PATH).exchange(this::toEntity);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
    }

    @Test
    void invalidToken_onProtectedEndpoint_isRejectedUnauthenticated() {
        ResponseEntity<String> response = getWithBearer(PROTECTED_PATH, "not-a-real-jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
    }

    @Test
    void expiredToken_onProtectedEndpoint_isRejectedUnauthenticated() {
        // Forged with the same shape a real token has, but an exp already in
        // the past - JwtService#parseToken rejects it as expired, same as
        // any other invalid token (see JwtAuthenticationFilter class
        // javadoc: every "no valid token" path collapses to the same 401).
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", Role.CLIENT.name())
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        ResponseEntity<String> response = getWithBearer(PROTECTED_PATH, expired);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
    }

    @Test
    void wrongRole_onClientOnlyEndpoint_isRejectedForbidden() {
        String agentToken = jwtService.issueToken(UUID.randomUUID(), Role.AGENT);

        ResponseEntity<String> response = getWithBearer(CLIENT_ONLY_PATH, agentToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
    }

    @Test
    void correctRole_onClientOnlyEndpoint_succeeds() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);

        ResponseEntity<String> response = getWithBearer(CLIENT_ONLY_PATH, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void validToken_onAuthenticatedOnlyEndpoint_succeedsRegardlessOfRole() {
        String agentToken = jwtService.issueToken(UUID.randomUUID(), Role.AGENT);

        ResponseEntity<String> response = getWithBearer(PROTECTED_PATH, agentToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void corsPreflight_onProtectedEndpoint_isPermittedForTheViteDevOrigin() {
        // The spec's frozen Boundaries & Constraints require CORS to keep
        // working through the new Security filter chain (preflight + actual
        // cross-origin requests from the Vite dev origin) - the chain sits
        // in front of every request, including this protected one, so a
        // browser's preflight OPTIONS must succeed here independently of the
        // authentication outcome of the real request that would follow it.
        ResponseEntity<String> response = client().options()
                .uri(PROTECTED_PATH)
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .exchange(this::toEntity);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:5173");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .isEqualToIgnoringCase("authorization");
    }

    @Test
    void healthEndpoint_staysReachableWithNoToken() {
        ResponseEntity<String> response =
                client().get().uri("/actuator/health").exchange(this::toEntity);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void registerAndLogin_stayReachableWithNoTokenAndUnchangedFromStory13() {
        String email = "story14-" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        String registerBody = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
        ResponseEntity<String> registerResponse = postJson("/api/v1/auth/register", registerBody);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String loginBody = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
        ResponseEntity<String> loginResponse = postJson("/api/v1/auth/login", loginBody);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).contains("\"token\"");

        // Also proves login's own 401 (wrong password) is still the auth
        // module's AUTH_INVALID_CREDENTIALS - not the security filter chain's
        // AUTH_UNAUTHENTICATED - i.e. the public permitAll rule is actually
        // letting the request reach AuthenticationService, not silently
        // rejecting it before AuthController ever runs.
        String wrongPasswordBody = "{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email);
        ResponseEntity<String> wrongPasswordResponse = postJson("/api/v1/auth/login", wrongPasswordBody);
        assertThat(wrongPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPasswordResponse.getBody()).contains("\"code\":\"AUTH_INVALID_CREDENTIALS\"");
    }

    private ResponseEntity<String> getWithBearer(String path, String token) {
        return client().get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange(this::toEntity);
    }

    private ResponseEntity<String> postJson(String path, String jsonBody) {
        return client().post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .exchange(this::toEntity);
    }

    private ResponseEntity<String> toEntity(
            HttpRequest request, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        // .headers(...) is required, not optional: without it, every response
        // built here silently drops the real HTTP response headers (status
        // and body only) - harmless for the existing status/body-only
        // assertions, but it would make any header-based assertion (e.g. the
        // CORS test below) always see null regardless of what the server
        // actually sent, which is exactly the failure this fixes.
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.bodyTo(String.class));
    }

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    /**
     * Test-only controller (never shipped - src/test/java only). Proves the
     * shared gate mechanism before Story 1.5's real Quote endpoints exist to
     * consume it.
     */
    @RestController
    static class TestProtectedController {

        @GetMapping(PROTECTED_PATH)
        public String protectedEndpoint() {
            return "ok";
        }

        @GetMapping(CLIENT_ONLY_PATH)
        @PreAuthorize("hasRole('CLIENT')")
        public String clientOnlyEndpoint() {
            return "ok";
        }
    }
}
