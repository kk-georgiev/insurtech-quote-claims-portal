package com.motorinsurance.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.motorinsurance.auth.application.JwtService;
import com.motorinsurance.auth.domain.Role;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
    // Mirrors shared.config.ClockConfig's business zone (Story 6.2, AD-6) -
    // used only to compute the *expected* validUntil in these assertions,
    // never to drive the app itself.
    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");

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
        assertThat(response.getBody()).contains("\"bonusMalusClass\":\"NEUTRAL\"");
        assertThat(response.getBody()).contains("\"bonusMalusFactor\":1.000");
        assertThat(response.getBody()).containsPattern("\"createdAt\":\"[^\"]+\"");
        // Story 6.2: a freshly-calculated quote is CALCULATED, valid for the
        // configured offer-validity window (quote.offer-validity-days: 14),
        // and not yet accepted.
        assertThat(response.getBody()).contains("\"status\":\"CALCULATED\"");
        assertThat(response.getBody()).contains("\"acceptedAt\":null");
        assertThat(response.getBody())
                .containsPattern("\"validUntil\":\"" + LocalDate.now(SOFIA_ZONE).plusDays(14) + "\"");
    }

    @Test
    void clientRole_bonusClass_reducesOneTimePremiumBeforeInstallmentFee() {
        // Story 6.1: BONUS_20 (factor 0.800) applies to (base + age surcharge)
        // only - the installment fee is untouched, per the fixed order of
        // operations (Architecture Spine AD-8, M3). Same KH/1500cc/age 20
        // inputs as the known-inputs case above: base 141.12 + surcharge
        // 36.00 = 177.12; x0.800 = 141.696, HALF_UP to 141.70; + fee 2.00 =
        // 143.70; /2 = 71.85.
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        String body =
                "{\"driverAge\":20,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2,\"bonusMalusClass\":\"BONUS_20\"}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"basePremium\":141.12");
        assertThat(response.getBody()).contains("\"ageSurcharge\":36.00");
        assertThat(response.getBody()).contains("\"bonusMalusClass\":\"BONUS_20\"");
        assertThat(response.getBody()).contains("\"bonusMalusFactor\":0.800");
        assertThat(response.getBody()).contains("\"oneTimePremium\":141.70");
        assertThat(response.getBody()).contains("\"installmentFee\":2.00");
        assertThat(response.getBody()).contains("\"totalPremium\":143.70");
        assertThat(response.getBody()).contains("\"installmentAmount\":71.85");
    }

    @Test
    void clientRole_malusClass_increasesOneTimePremiumBeforeInstallmentFee() {
        // MALUS_50 (factor 1.500): 177.12 x1.500 = 265.68; + fee 2.00 =
        // 267.68; /2 = 133.84.
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        String body =
                "{\"driverAge\":20,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2,\"bonusMalusClass\":\"MALUS_50\"}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"bonusMalusClass\":\"MALUS_50\"");
        assertThat(response.getBody()).contains("\"bonusMalusFactor\":1.500");
        assertThat(response.getBody()).contains("\"oneTimePremium\":265.68");
        assertThat(response.getBody()).contains("\"totalPremium\":267.68");
        assertThat(response.getBody()).contains("\"installmentAmount\":133.84");
    }

    @Test
    void clientRole_unknownBonusMalusClass_returnsFieldLevelError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body =
                "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":1,\"bonusMalusClass\":\"NOT_A_CLASS\"}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"PRICING_UNKNOWN_BONUS_MALUS_CLASS\"");
        assertThat(response.getBody()).contains("\"field\":\"bonusMalusClass\"");
    }

    @Test
    void clientRole_blankBonusMalusClass_returnsFieldLevelValidationError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":1,\"bonusMalusClass\":\"\"}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"bonusMalusClass\"");
    }

    @Test
    void clientRole_regionCodeLowercase_isNormalizedAndStillSucceeds() {
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        String body = "{\"driverAge\":20,\"regionCode\":\"kh\",\"engineCc\":1500,\"installments\":2,\"bonusMalusClass\":\"NEUTRAL\"}";

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
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":65540,\"bonusMalusClass\":\"NEUTRAL\"}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"installments\"");
    }

    @Test
    void clientRole_unknownRegionCode_returnsFieldLevelError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"ZZ\",\"engineCc\":1000,\"installments\":1,\"bonusMalusClass\":\"NEUTRAL\"}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"PRICING_UNKNOWN_REGION\"");
        assertThat(response.getBody()).contains("\"field\":\"regionCode\"");
    }

    @Test
    void clientRole_unsupportedInstallmentCount_returnsFieldLevelError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":3,\"bonusMalusClass\":\"NEUTRAL\"}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"PRICING_UNSUPPORTED_INSTALLMENTS\"");
        assertThat(response.getBody()).contains("\"field\":\"installments\"");
    }

    @Test
    void clientRole_driverAgeUnderEighteen_returnsFieldLevelValidationError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":17,\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":1,\"bonusMalusClass\":\"NEUTRAL\"}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"field\":\"driverAge\"");
    }

    @Test
    void clientRole_engineCcBelowEightHundred_returnsFieldLevelValidationError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":700,\"installments\":1,\"bonusMalusClass\":\"NEUTRAL\"}";

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
        String body = "{\"driverAge\":100,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2,\"bonusMalusClass\":\"NEUTRAL\"}";

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
        String body = "{\"driverAge\":101,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2,\"bonusMalusClass\":\"NEUTRAL\"}";

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
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":8000,\"installments\":2,\"bonusMalusClass\":\"NEUTRAL\"}";

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
        String body = "{\"driverAge\":30,\"regionCode\":\"KH\",\"engineCc\":8001,\"installments\":2,\"bonusMalusClass\":\"NEUTRAL\"}";

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
        String body = "{\"driverAge\":100000,\"regionCode\":\"KH\",\"engineCc\":10000000,\"installments\":2,\"bonusMalusClass\":\"NEUTRAL\"}";

        ResponseEntity<String> response = postJson(body, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"driverAge\"");
    }

    @Test
    void clientRole_malformedRequestBody_isBadRequestNotServerError() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
        String malformed = "{\"driverAge\":\"not-a-number\",\"regionCode\":\"KH\",\"engineCc\":1000,\"installments\":1,\"bonusMalusClass\":\"NEUTRAL\"}";

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

    // --- Story 6.3: GET /api/v1/quotes (list) ---

    @Test
    void clientRole_listQuotes_returnsOnlyOwnQuotesNewestFirst() {
        String ownerToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        String otherToken = jwtService.issueToken(registerClient(), Role.CLIENT);

        UUID first = extractId(postJson(validRequestBody(), ownerToken).getBody());
        UUID second = extractId(postJson(validRequestBody(), ownerToken).getBody());
        // Belongs to a different customer - must never appear in the owner's list.
        postJson(validRequestBody(), otherToken);

        ResponseEntity<String> response = getWithBearer(QUOTES_PATH, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Bare JSON array (Architecture Spine AD-12) - not an envelope object.
        assertThat(response.getBody()).startsWith("[").endsWith("]");
        List<UUID> ids = extractAllIds(response.getBody());
        assertThat(ids).containsExactly(second, first); // newest first
    }

    @Test
    void clientRole_listQuotes_ownerScopedEvenAgainstManyOtherCustomers() {
        String ownerToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        jwtService.issueToken(registerClient(), Role.CLIENT); // another customer, no quotes of their own here
        String otherToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        postJson(validRequestBody(), otherToken);
        postJson(validRequestBody(), otherToken);

        ResponseEntity<String> response = getWithBearer(QUOTES_PATH, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    void clientRole_listQuotes_noQuotesYet_returnsEmptyArrayNotError() {
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);

        ResponseEntity<String> response = getWithBearer(QUOTES_PATH, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    void clientRole_listQuotes_eachEntryCarriesTheFullBreakdownShape() {
        // The list endpoint returns the same DTO the detail endpoint does
        // (AD-12) - not a slimmed summary. Spot-checks one field from each
        // area of the response (input, breakdown, lifecycle) rather than
        // repeating the full-body assertion already covered elsewhere.
        String clientToken = jwtService.issueToken(registerClient(), Role.CLIENT);
        postJson(validRequestBody(), clientToken);

        ResponseEntity<String> response = getWithBearer(QUOTES_PATH, clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"regionCode\":\"KH\"");
        assertThat(response.getBody()).contains("\"totalPremium\":179.12");
        assertThat(response.getBody()).contains("\"status\":\"CALCULATED\"");
        assertThat(response.getBody()).contains("\"acceptedAt\":null");
    }

    @Test
    void noToken_onList_isRejectedUnauthenticated() {
        ResponseEntity<String> response = getWithBearer(QUOTES_PATH, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
    }

    @Test
    void nonClientRole_onList_isRejectedForbidden() {
        String agentToken = jwtService.issueToken(UUID.randomUUID(), Role.AGENT);

        ResponseEntity<String> response = getWithBearer(QUOTES_PATH, agentToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
    }

    private static List<UUID> extractAllIds(String responseBody) {
        Matcher matcher = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"").matcher(responseBody);
        List<UUID> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(UUID.fromString(matcher.group(1)));
        }
        return ids;
    }

    private String validRequestBody() {
        return "{\"driverAge\":20,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2,\"bonusMalusClass\":\"NEUTRAL\"}";
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
