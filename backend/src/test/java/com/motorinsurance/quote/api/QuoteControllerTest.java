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

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Every field the response carries, pinned to the PRD addendum's
        // worked example (KH / age 20 / 1500cc / 2 installments) - the same
        // values PricingServiceTest.calculate_knownInputs_returnsExactExpectedBreakdown
        // asserts on PricingResult. The create<->retrieve equality check in
        // clientRole_calculateThenRetrieveById is symmetric (both bodies flow
        // through the same toResponse), so a transposition present on both
        // paths - e.g. basePremium<->ageSurcharge - only fails here.
        assertThat(response.getBody()).contains("\"driverAge\":20");
        assertThat(response.getBody()).contains("\"regionCode\":\"KH\"");
        assertThat(response.getBody()).contains("\"engineCc\":1500");
        assertThat(response.getBody()).contains("\"zoneId\":1");
        assertThat(response.getBody()).contains("\"zoneName\":\"Zone 1\"");
        assertThat(response.getBody()).contains("\"basePremium\":141.12");
        assertThat(response.getBody()).contains("\"ageSurcharge\":36.00");
        assertThat(response.getBody()).contains("\"oneTimePremium\":177.12");
        assertThat(response.getBody()).contains("\"installments\":2");
        assertThat(response.getBody()).contains("\"installmentFee\":2.00");
        assertThat(response.getBody()).contains("\"totalPremium\":179.12");
        assertThat(response.getBody()).contains("\"installmentAmount\":89.56");
        assertThat(response.getBody()).contains("\"currency\":\"EUR\"");
        assertThat(response.getBody()).containsPattern("\"createdAt\":\"[^\"]+\"");
    }

    @Test
    void clientRole_regionCodeLowercase_isNormalizedAndStillSucceeds() {
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        String body = "{\"driverAge\":20,\"regionCode\":\"kh\",\"engineCc\":1500,\"installments\":2}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"totalPremium\":179.12");
        // Review-loop finding, Story 1.6: the persisted/returned regionCode
        // must be the canonical form actually priced against, not whatever
        // case the client sent - otherwise the record would show "kh"
        // alongside a "Zone 1"/zoneId derived from "KH", inconsistent with
        // itself.
        assertThat(response.getBody()).contains("\"regionCode\":\"KH\"");
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
    void clientRole_driverAgeAtCeiling_isAcceptedAndPriced() {
        // driverAge=100 is the new @Max ceiling itself - still within the
        // tariff's open-ended 86+ band, so this must still price (201), not
        // be rejected. spec-quote-input-bounds.md I/O matrix.
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        String body = "{\"driverAge\":100,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Full breakdown, not just the echoed input - same rationale as
        // clientRole_validInput_returnsFullBreakdown: KH/zone1/1301-2100cc
        // base rate (141.12) plus the 86+ band's +10.00 age surcharge.
        assertThat(response.getBody()).contains("\"driverAge\":100");
        assertThat(response.getBody()).contains("\"regionCode\":\"KH\"");
        assertThat(response.getBody()).contains("\"engineCc\":1500");
        assertThat(response.getBody()).contains("\"zoneId\":1");
        assertThat(response.getBody()).contains("\"zoneName\":\"Zone 1\"");
        assertThat(response.getBody()).contains("\"basePremium\":141.12");
        assertThat(response.getBody()).contains("\"ageSurcharge\":10.00");
        assertThat(response.getBody()).contains("\"oneTimePremium\":151.12");
        assertThat(response.getBody()).contains("\"installments\":2");
        assertThat(response.getBody()).contains("\"installmentFee\":2.00");
        assertThat(response.getBody()).contains("\"totalPremium\":153.12");
        assertThat(response.getBody()).contains("\"installmentAmount\":76.56");
        assertThat(response.getBody()).contains("\"currency\":\"EUR\"");
    }

    @Test
    void clientRole_driverAgeOverCeiling_returnsFieldLevelValidationError() {
        // Reproduces the originally-reported bug (driverAge=100000 silently
        // priced) at a tighter boundary just above the new @Max(100) ceiling.
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":101,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"driverAge\"");
    }

    @Test
    void clientRole_engineCcAtCeiling_isAcceptedAndPriced() {
        // engineCc=8000 is the new @Max ceiling itself - still within the
        // tariff's open-ended 2501+ band, so this must still price (201).
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":8000,\"installments\":2}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Full breakdown, not just the echoed input - same rationale as
        // clientRole_validInput_returnsFullBreakdown: KH/zone1's open-ended
        // 2501+ band (166.17) with no age surcharge (driverAge=30 is 25-85).
        assertThat(response.getBody()).contains("\"driverAge\":30");
        assertThat(response.getBody()).contains("\"regionCode\":\"KH\"");
        assertThat(response.getBody()).contains("\"engineCc\":8000");
        assertThat(response.getBody()).contains("\"zoneId\":1");
        assertThat(response.getBody()).contains("\"zoneName\":\"Zone 1\"");
        assertThat(response.getBody()).contains("\"basePremium\":166.17");
        assertThat(response.getBody()).contains("\"ageSurcharge\":0.00");
        assertThat(response.getBody()).contains("\"oneTimePremium\":166.17");
        assertThat(response.getBody()).contains("\"installments\":2");
        assertThat(response.getBody()).contains("\"installmentFee\":2.00");
        assertThat(response.getBody()).contains("\"totalPremium\":168.17");
        assertThat(response.getBody()).contains("\"installmentAmount\":84.09");
        assertThat(response.getBody()).contains("\"currency\":\"EUR\"");
    }

    @Test
    void clientRole_engineCcOverCeiling_returnsFieldLevelValidationError() {
        // Reproduces the originally-reported bug (engineCc=10000000 silently
        // priced) at a tighter boundary just above the new @Max(8000) ceiling.
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":8001,\"installments\":2}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"engineCc\"");
    }

    @Test
    void clientRole_originallyReportedBugValues_returnsFieldLevelValidationError() {
        // Pins the exact regression manually found (spec Intent /
        // "Previously-reported case" row of the I/O matrix): driverAge=100000
        // and engineCc=10000000 both used to submit successfully and produce
        // a priced quote. The boundary tests above (101/8001) cover the
        // ceiling edge; this covers the literal reported values themselves.
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":100000,\"regionCode\":\"KH\",\"engineCc\":10000000,\"installments\":2}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"driverAge\"");
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
    void clientRole_tokenForNonexistentCustomer_returnsUnauthenticatedNotServerError() {
        // A forged token for a customer id with no matching users row - see
        // registerClient()'s javadoc. quotes.customer_id's foreign key used
        // to surface this as an unhandled DataIntegrityViolationException
        // (epic-1-retro-item-5); it must now collapse to the same clean 401
        // the JWT gate itself uses for "this token doesn't identify a real,
        // currently-valid account".
        String tokenForMissingCustomer = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);

        ResponseEntity<String> response = postJson(validRequestBody(), tokenForMissingCustomer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
    }

    @Test
    void clientRole_calculateThenRetrieveById_returnsTheSamePersistedQuote() {
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);

        ResponseEntity<String> createResponse = postJson(validRequestBody(), clientToken);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID quoteId = extractId(createResponse.getBody());

        ResponseEntity<String> getResponse = getWithBearer(QUOTES_PATH + "/" + quoteId, clientToken);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Review-loop finding, Story 1.6: the AC asks for "the full original
        // quote" back, not just a couple of spot-checked fields - comparing
        // the entire body catches a mapping bug in QuoteService.toResponse
        // dropping or mismatching any field, not just the two checked below.
        // createdAt is normalized out first: Postgres TIMESTAMPTZ rounds to
        // microseconds, so the create response's in-memory Instant.now()
        // (nanosecond precision) and the retrieve response's freshly-read
        // value can render as different strings for the exact same instant.
        assertThat(withoutCreatedAt(getResponse.getBody())).isEqualTo(withoutCreatedAt(createResponse.getBody()));
        assertThat(getResponse.getBody()).contains("\"id\":\"" + quoteId + "\"");
        assertThat(getResponse.getBody()).contains("\"driverAge\":20");
        assertThat(getResponse.getBody()).contains("\"regionCode\":\"KH\"");
        assertThat(getResponse.getBody()).contains("\"engineCc\":1500");
        assertThat(getResponse.getBody()).contains("\"totalPremium\":179.12");
        assertThat(getResponse.getBody()).containsPattern("\"createdAt\":\"[^\"]+\"");
        // createdAt must be the persisted instant, not a value regenerated per
        // call: a second retrieval must render byte-identically to the first
        // (a toResponse that passed Instant.now() would drift between calls).
        ResponseEntity<String> getAgain = getWithBearer(QUOTES_PATH + "/" + quoteId, clientToken);
        assertThat(getAgain.getBody()).isEqualTo(getResponse.getBody());
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

    @Test
    void nonClientRole_onGetById_isRejectedForbidden() {
        // Review-loop finding, Story 1.6: the sibling POST endpoint already
        // had this test - GET's identical @PreAuthorize("hasRole('CLIENT')")
        // had no equivalent, so a regression weakening/removing it would
        // have shipped with nothing failing.
        String agentToken = jwtService.issueToken(UUID.randomUUID(), Role.AGENT);

        ResponseEntity<String> response = getWithBearer(QUOTES_PATH + "/" + UUID.randomUUID(), agentToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
    }

    @Test
    void clientRole_malformedQuoteId_returnsFieldLevelValidationError() {
        // Review-loop finding, Story 1.6: a non-UUID path segment used to
        // fall through to the generic 500 handler instead of a clean 400.
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);

        ResponseEntity<String> response = getWithBearer(QUOTES_PATH + "/not-a-uuid", clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"id\"");
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

    private static String withoutCreatedAt(String responseBody) {
        return responseBody.replaceAll("\"createdAt\":\"[^\"]*\"", "\"createdAt\":\"<omitted>\"");
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
