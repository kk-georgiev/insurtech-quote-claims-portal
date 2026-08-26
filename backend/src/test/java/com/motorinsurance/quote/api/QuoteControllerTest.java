package com.motorinsurance.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.motorinsurance.auth.application.JwtService;
import com.motorinsurance.auth.domain.Role;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack HTTP proof for {@code POST /api/v1/quotes} (Story 1.5) and
 * {@code GET /api/v1/quotes/{id}} (Story 1.6) - the first real consumer of
 * Story 1.4's shared JWT gate, following the same {@code RestClient} +
 * random-port pattern as {@code auth.config.JwtAuthenticationFilterTest}. A
 * real Postgres (Testcontainers) backs it, same rationale as {@link
 * com.motorinsurance.pricing.application.PricingServiceTest}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class QuoteControllerTest {

    private static final String QUOTES_PATH = "/api/v1/quotes";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Test
    void noToken_isRejectedUnauthenticated() {
        ResponseEntity<String> response = postJson(validRequestBody(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
    }

    @Test
    void nonClientRole_isRejectedForbidden() {
        String agentToken = jwtService.issueToken(UUID.randomUUID(), Role.AGENT);

        ResponseEntity<String> response = postJson(validRequestBody(), agentToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
    }

    @Test
    void clientRole_validInput_returnsFullBreakdown() {
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);

        ResponseEntity<String> response = postJson(validRequestBody(), clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"zoneName\":\"Zone 1\"");
        assertThat(response.getBody()).contains("\"totalPremium\":179.12");
        assertThat(response.getBody()).contains("\"installmentAmount\":89.56");
    }

    @Test
    void clientRole_regionCodeLowercase_isNormalizedAndStillSucceeds() {
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        String body = "{\"driverAge\":20,\"regionCode\":\"kh\",\"engineCc\":1500,\"installments\":2}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"totalPremium\":179.12");
    }

    @Test
    void clientRole_installmentsAboveFour_returnsFieldLevelValidationError() {
        // Also covers the int->short overflow case (65540 aliases to a valid
        // plan without this bound) - both are values @Max(4) rejects the
        // same way, before PricingService's own narrowing cast ever runs.
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":65540}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"installments\"");
    }

    @Test
    void clientRole_unknownRegionCode_returnsFieldLevelError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"ZZ\",\"engineCc\":1000,\"installments\":1}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"PRICING_UNKNOWN_REGION\"");
        assertThat(response.getBody()).contains("\"field\":\"regionCode\"");
    }

    @Test
    void clientRole_unsupportedInstallmentCount_returnsFieldLevelError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":3}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"PRICING_UNSUPPORTED_INSTALLMENTS\"");
        assertThat(response.getBody()).contains("\"field\":\"installments\"");
    }

    @Test
    void clientRole_driverAgeUnderEighteen_returnsFieldLevelValidationError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":17,\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":1}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"field\":\"driverAge\"");
    }

    @Test
    void clientRole_engineCcBelowEightHundred_returnsFieldLevelValidationError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":700,\"installments\":1}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"field\":\"engineCc\"");
    }

    @Test
    void clientRole_malformedRequestBody_isBadRequestNotServerError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String malformed = "{\"driverAge\":\"not-a-number\",\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":1}";

        ResponseEntity<String> response = postJson(malformed, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        // Review-loop finding, Story 1.5: this handler used to return no
        // field information at all, unlike every other 400 in the API.
        assertThat(response.getBody()).contains("\"field\":\"driverAge\"");
    }

    @Test
    void clientRole_calculateThenRetrieveById_returnsTheSamePersistedQuote() {
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);

        ResponseEntity<String> createResponse = postJson(validRequestBody(), clientToken);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID quoteId = extractId(createResponse.getBody());

        ResponseEntity<String> getResponse = getWithBearer(QUOTES_PATH + "/" + quoteId, clientToken);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).contains("\"id\":\"" + quoteId + "\"");
        assertThat(getResponse.getBody()).contains("\"totalPremium\":179.12");
    }

    @Test
    void clientRole_retrieveAnotherCustomersQuote_returnsNotFoundNotForbidden() {
        String ownerToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        String otherClientToken = jwtService.issueToken(registerClient(), Role.CLIENT);

        ResponseEntity<String> createResponse = postJson(validRequestBody(), ownerToken);
        UUID quoteId = extractId(createResponse.getBody());

        // 404, not 403: the response must not confirm the id belongs to
        // someone else (see QuoteRepository/QuoteNotFoundException javadoc).
        ResponseEntity<String> getResponse = getWithBearer(QUOTES_PATH + "/" + quoteId, otherClientToken);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getResponse.getBody()).contains("\"code\":\"QUOTE_NOT_FOUND\"");
    }

    @Test
    void clientRole_retrieveNonexistentQuoteId_returnsNotFound() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);

        ResponseEntity<String> response = getWithBearer(QUOTES_PATH + "/" + UUID.randomUUID(), clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"QUOTE_NOT_FOUND\"");
    }

    @Test
    void noToken_onGetById_isRejectedUnauthenticated() {
        ResponseEntity<String> response = getWithBearer(QUOTES_PATH + "/" + UUID.randomUUID(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
    }

    private String validRequestBody() {
        return "{\"driverAge\":20,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2}";
    }

    /**
     * Registers a real client via the actual HTTP endpoint (rather than an
     * arbitrary {@code UUID.randomUUID()}) and returns their id, then a test
     * mints a token for it directly via {@link JwtService} - one real HTTP
     * round trip instead of two (register + login). Required since
     * {@code quotes.customer_id} has a foreign key to {@code users}
     * (V4__create_quotes_table.sql): a forged token for a nonexistent user,
     * fine for the auth-gate-only tests in {@code JwtAuthenticationFilterTest},
     * would fail here with a constraint violation the moment a quote is
     * actually persisted.
     */
    private UUID registerClient() {
        String email = "quote-test-" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email);
        ResponseEntity<String> response = client().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange(this::toEntity);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return extractId(response.getBody());
    }

    private static UUID extractId(String responseBody) {
        Matcher matcher = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"").matcher(responseBody);
        assertThat(matcher.find()).as("response body should contain an \"id\" field: %s", responseBody)
                .isTrue();
        return UUID.fromString(matcher.group(1));
    }

    private ResponseEntity<String> postJson(String jsonBody, String bearerToken) {
        RestClient.RequestBodySpec spec =
                client().post().uri(QUOTES_PATH).contentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return spec.body(jsonBody).exchange(this::toEntity);
    }

    private ResponseEntity<String> getWithBearer(String path, String bearerToken) {
        RestClient.RequestHeadersSpec<?> spec = client().get().uri(path);
        if (bearerToken != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return spec.exchange(this::toEntity);
    }

    private ResponseEntity<String> toEntity(
            HttpRequest request, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.bodyTo(String.class));
    }

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }
}
