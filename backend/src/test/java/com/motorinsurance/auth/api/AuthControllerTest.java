package com.motorinsurance.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.motorinsurance.auth.application.JwtService;
import com.motorinsurance.auth.domain.Role;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack HTTP proof for the auth module's own endpoints -
 * {@code POST /api/v1/auth/register} (Story 1.2) and
 * {@code POST /api/v1/auth/login} (Story 1.3) - added by Epic 1 retro action
 * item 1: the module had {@code JwtServiceTest} and
 * {@code JwtAuthenticationFilterTest} but nothing exercising registration,
 * the duplicate-email 409, the unknown-email login path, or a token that
 * actually came out of a real {@code /login} call.
 *
 * <p>Same {@code RestClient} + random-port + Testcontainers-Postgres pattern
 * as {@code quote.api.QuoteControllerTest} and
 * {@code auth.config.JwtAuthenticationFilterTest}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthControllerTest {

    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String PASSWORD = "password123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Test
    void register_validInput_returnsCreatedWithClientUser() {
        String email = uniqueEmail();

        ResponseEntity<String> response = postJson(REGISTER_PATH, credentials(email, PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"email\":\"" + email + "\"");
        assertThat(response.getBody()).contains("\"role\":\"CLIENT\"");
        assertThat(response.getBody()).doesNotContain("password");
    }

    @Test
    void register_duplicateEmail_returnsConflictWithEmailFieldError() {
        String email = uniqueEmail();
        assertThat(postJson(REGISTER_PATH, credentials(email, PASSWORD)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = postJson(REGISTER_PATH, credentials(email, PASSWORD));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("\"code\":\"AUTH_EMAIL_TAKEN\"");
        // Epic 1 retro action item 2: the 409 now names the offending field.
        assertThat(second.getBody()).contains("\"field\":\"email\"");
    }

    @Test
    void register_duplicateEmailDifferentCasing_isAlsoRejected() {
        String local = "case-" + UUID.randomUUID();
        assertThat(postJson(REGISTER_PATH, credentials(local + "@example.com", PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> mixedCase =
                postJson(REGISTER_PATH, credentials(local.toUpperCase() + "@Example.COM", PASSWORD));

        assertThat(mixedCase.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mixedCase.getBody()).contains("\"code\":\"AUTH_EMAIL_TAKEN\"");
    }

    @Test
    void registerThenLogin_issuesTokenCarryingThatUsersRealIdAndRole() {
        String email = uniqueEmail();
        ResponseEntity<String> register = postJson(REGISTER_PATH, credentials(email, PASSWORD));
        UUID registeredId = extractField(register.getBody(), "id", AuthControllerTest::asUuid);

        ResponseEntity<String> login = postJson(LOGIN_PATH, credentials(email, PASSWORD));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = extractField(login.getBody(), "token", value -> value);

        // The point of the item: decode a token that actually came out of
        // /login (not one hand-minted via jwtService.issueToken like every
        // other test does) and prove it carries this user's real identity.
        JwtService.ParsedToken parsed = jwtService.parseToken(token);
        assertThat(parsed.userId()).isEqualTo(registeredId);
        assertThat(parsed.role()).isEqualTo(Role.CLIENT);
    }

    @Test
    void login_unknownEmail_returnsUnauthorizedInvalidCredentials() {
        ResponseEntity<String> response = postJson(LOGIN_PATH, credentials(uniqueEmail(), PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_INVALID_CREDENTIALS\"");
        assertThat(response.getBody()).doesNotContain("\"fieldErrors\":[{");
    }

    @Test
    void login_wrongPassword_returnsUnauthorizedInvalidCredentials() {
        String email = uniqueEmail();
        postJson(REGISTER_PATH, credentials(email, PASSWORD));

        ResponseEntity<String> response = postJson(LOGIN_PATH, credentials(email, "not-the-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_INVALID_CREDENTIALS\"");
    }

    @Test
    void login_emailCaseInsensitive_succeeds() {
        // Registered lower-case, logging in mixed-case: the shared
        // Emails.normalize on both sides is what makes this match against
        // users.email's case-sensitive UNIQUE column (retro action item 3).
        String local = "login-" + UUID.randomUUID();
        postJson(REGISTER_PATH, credentials(local + "@example.com", PASSWORD));

        ResponseEntity<String> response =
                postJson(LOGIN_PATH, credentials(local.toUpperCase() + "@Example.COM", PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"token\"");
    }

    private static String uniqueEmail() {
        return "auth-test-" + UUID.randomUUID() + "@example.com";
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static UUID asUuid(String raw) {
        return UUID.fromString(raw);
    }

    private static <T> T extractField(String responseBody, String field, Function<String, T> mapper) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(responseBody);
        assertThat(matcher.find())
                .as("response body should contain a \"%s\" field: %s", field, responseBody)
                .isTrue();
        return mapper.apply(matcher.group(1));
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
        return ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class));
    }

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }
}
