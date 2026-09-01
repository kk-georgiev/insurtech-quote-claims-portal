package com.motorinsurance.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.motorinsurance.auth.application.JwtService;
import com.motorinsurance.auth.domain.Role;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack HTTP proof for {@code POST /api/v1/quotes/{id}/accept} (Story
 * 8.1) against a real Postgres, following the {@code RestClient} +
 * random-port pattern {@code QuoteControllerTest} established.
 *
 * <p>Deliberately its own class rather than more methods on that already
 * 644-line file (Epic 6 retro action item 45): the accept endpoint is a
 * different concern with its own fixtures - a persisted quote to accept, a
 * {@link JdbcTemplate} for the states HTTP alone cannot create (expiring a
 * quote, counting policy rows, mutating the tariff underneath an issued
 * policy, planting a policy the application does not know about), and a
 * real concurrent burst of requests.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class QuoteAcceptanceControllerTest {

    private static final String QUOTES_PATH = "/api/v1/quotes";
    // Mirrors shared.config.ClockConfig's business zone (AD-6) - used only
    // to compute expected dates here, never to drive the app itself.
    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");
    private static final Pattern POLICY_NUMBER = Pattern.compile("^MI-\\d{4}-\\d{8}$");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** The same configured period PolicyService derives coverage from - not a literal 12 restated here. */
    @Value("${policy.coverage-months}")
    private int coverageMonths;

    @Test
    void clientRole_acceptsOwnValidQuote_issuesOnePolicyWithTheQuotesOwnFigures() {
        String token = clientToken();
        UUID quoteId = createQuote(token);
        LocalDate start = today();

        ResponseEntity<String> response = accept(quoteId, acceptBody(start, "Ivan Petrov", "CA1234BM", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();
        assertThat(POLICY_NUMBER.matcher(extract(body, "policyNumber")).matches())
                .as("policy number should be MI-{year}-{8 digits}: %s", body)
                .isTrue();
        assertThat(extract(body, "policyNumber")).startsWith("MI-" + start.getYear() + "-");
        assertThat(extract(body, "quoteId")).isEqualTo(quoteId.toString());
        assertThat(extract(body, "holderName")).isEqualTo("Ivan Petrov");
        assertThat(extract(body, "vehicleRegistration")).isEqualTo("CA1234BM");
        assertThat(body).contains("\"vehicleVin\":null");
        assertThat(extract(body, "coverageStart")).isEqualTo(start.toString());
        // Inclusive at both ends (AD-6): twelve months of cover ends the day
        // before the anniversary, never on it. Derived from the configured
        // period rather than a literal, so a changed configuration moves the
        // expectation with the code instead of silently disagreeing with it.
        assertThat(extract(body, "coverageEnd"))
                .isEqualTo(start.plusMonths(coverageMonths).minusDays(1).toString());
        assertThat(body).containsPattern("\"issuedAt\":\"[^\"]+\"");

        // Every rating input is carried across too, not just the money: with
        // 24 same-typed constructor arguments between the quote and the
        // policy, a transposition is only caught by asserting each field.
        assertThat(body).contains("\"driverAge\":20");
        assertThat(body).contains("\"regionCode\":\"KH\"");
        assertThat(body).contains("\"engineCc\":1500");
        assertThat(body).contains("\"zoneId\":1");
        assertThat(body).contains("\"zoneName\":\"Zone 1\"");
        assertThat(body).contains("\"installments\":2");
        assertThat(body).contains("\"installmentFee\":2.00");

        // The premium is copied from the quote, never recalculated (NFR-1) -
        // the same worked example QuoteControllerTest pins on the quote side.
        assertThat(body).contains("\"basePremium\":141.12");
        assertThat(body).contains("\"ageSurcharge\":36.00");
        assertThat(body).contains("\"bonusMalusClass\":\"NEUTRAL\"");
        assertThat(body).contains("\"bonusMalusFactor\":1.000");
        assertThat(body).contains("\"oneTimePremium\":177.12");
        assertThat(body).contains("\"totalPremium\":179.12");
        assertThat(body).contains("\"installmentAmount\":89.56");
        assertThat(body).contains("\"currency\":\"EUR\"");

        assertThat(policyCount(quoteId)).isEqualTo(1);
        // The quote now reads ACCEPTED through the same derivation every
        // other read path uses (AD-3) - no status column was introduced.
        String quote = getJson(QUOTES_PATH + "/" + quoteId, token).getBody();
        assertThat(quote).contains("\"status\":\"ACCEPTED\"");
        // A real timestamp, asserted as one: "does not contain null" would
        // also pass if the field vanished from the payload entirely.
        assertThat(quote).containsPattern("\"acceptedAt\":\"[^\"]+\"");
    }

    @Test
    void clientRole_coverageStartInTheFuture_isHonouredRatherThanReplacedByToday() {
        // The one case that distinguishes "stores what the client asked for"
        // from "stores today": every other issuing test here passes today(),
        // so an implementation that ignored the request field and used the
        // clock would satisfy them all (FR-M3-04 - the client chooses when
        // cover begins).
        String token = clientToken();
        UUID quoteId = createQuote(token);
        LocalDate requestedStart = today().plusDays(45);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(requestedStart, "Ivan Petrov", "CA1234BM", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(extract(response.getBody(), "coverageStart")).isEqualTo(requestedStart.toString());
        assertThat(extract(response.getBody(), "coverageEnd"))
                .isEqualTo(requestedStart.plusMonths(coverageMonths).minusDays(1).toString());
    }

    @Test
    void coveragePeriod_startingOnALeapDay_endsUnderTheSameMonthArithmetic() {
        // Pins the month-end behaviour of the configured period rather than
        // leaving it to whichever ordinary date the suite happens to run on:
        // 29 Feb 2028 + 12 months clamps to 28 Feb 2029, so the inclusive end
        // is the 27th. Documented here as the decided rule (AD-6's formula),
        // not discovered later by a client on a leap day.
        String token = clientToken();
        UUID quoteId = createQuote(token);
        LocalDate leapDayStart = LocalDate.of(2028, 2, 29);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(leapDayStart, "Ivan Petrov", "CA1234BM", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(extract(response.getBody(), "coverageStart")).isEqualTo("2028-02-29");
        assertThat(extract(response.getBody(), "coverageEnd")).isEqualTo("2029-02-27");
    }

    @Test
    void clientRole_acceptsWithVin_storesTheVinAndNoRegistration() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", null, "wdb1234567n123456"), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Normalized to the canonical uppercase form, like regionCode is.
        assertThat(extract(response.getBody(), "vehicleVin")).isEqualTo("WDB1234567N123456");
        assertThat(response.getBody()).contains("\"vehicleRegistration\":null");
    }

    @Test
    void clientRole_acceptsTwice_returnsTheSamePolicyWithOkNotAnError() {
        String token = clientToken();
        UUID quoteId = createQuote(token);
        String body = acceptBody(today(), "Ivan Petrov", "CA1234BM", null);

        ResponseEntity<String> first = accept(quoteId, body, token);
        String acceptedAtAfterFirst = acceptedAt(quoteId);
        ResponseEntity<String> replay = accept(quoteId, body, token);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Genuinely idempotent, not merely duplicate-protected (AD-5): a
        // caller retrying a dropped response gets the policy back, not a 409.
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractId(replay.getBody())).isEqualTo(extractId(first.getBody()));
        assertThat(extract(replay.getBody(), "policyNumber")).isEqualTo(extract(first.getBody(), "policyNumber"));
        assertThat(policyCount(quoteId)).isEqualTo(1);
        assertThat(acceptedAt(quoteId)).isEqualTo(acceptedAtAfterFirst);
    }

    @Test
    void clientRole_replayWithDifferentInput_stillReturnsTheOriginalPolicyUnchanged() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        ResponseEntity<String> first =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", "CA1234BM", null), token);
        ResponseEntity<String> replay = accept(
                quoteId, acceptBody(today().plusDays(30), "Someone Else", "CB9999XX", null), token);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The contract is immutable once issued (AD-4): a second call is a
        // read of what was agreed, never an amendment of it.
        assertThat(extract(replay.getBody(), "holderName")).isEqualTo("Ivan Petrov");
        assertThat(extract(replay.getBody(), "vehicleRegistration")).isEqualTo("CA1234BM");
        assertThat(extract(replay.getBody(), "coverageStart")).isEqualTo(extract(first.getBody(), "coverageStart"));
        assertThat(policyCount(quoteId)).isEqualTo(1);
    }

    @Test
    void clientRole_concurrentAccepts_issueExactlyOnePolicyAndNobodyErrors() throws Exception {
        String token = clientToken();
        UUID quoteId = createQuote(token);
        String body = acceptBody(today(), "Ivan Petrov", "CA1234BM", null);
        CountDownLatch release = new CountDownLatch(1);

        // The failure BA 19 names outright ("two policies on a double
        // click"), reproduced end to end: four in-flight requests released
        // together, not four sequential ones. Which of them takes the
        // pre-check path and which loses to the constraint is up to the
        // scheduler, so this is the system-level assertion - exactly one
        // creation, everyone else served the same policy, nobody an error.
        // The constraint-losing branch itself is pinned deterministically by
        // clientRole_lostUniqueConstraintRace_returnsTheExistingPolicyWithOk.
        int racers = 4;
        ExecutorService threads = Executors.newFixedThreadPool(racers);
        try {
            List<CompletableFuture<ResponseEntity<String>>> attempts = IntStream.range(0, racers)
                    .mapToObj(i ->
                            CompletableFuture.supplyAsync(() -> awaitThenAccept(release, quoteId, body, token), threads))
                    .toList();
            release.countDown();

            List<ResponseEntity<String>> responses =
                    attempts.stream().map(CompletableFuture::join).toList();

            assertThat(responses)
                    .as("no racer may see an error - a double click is a client's mistake, not a failure")
                    .allMatch(r -> r.getStatusCode().is2xxSuccessful());
            assertThat(responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count())
                    .as("exactly one request creates the policy; the rest are served the same one")
                    .isEqualTo(1);
            assertThat(responses.stream().map(r -> extractId(r.getBody())).distinct())
                    .hasSize(1);
            assertThat(policyCount(quoteId)).isEqualTo(1);
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void clientRole_lostUniqueConstraintRace_returnsTheExistingPolicyWithOk() {
        // The concurrent test above cannot fail for the reason it exists: if
        // its two requests happen not to overlap, the second one takes the
        // accepted_at pre-check and returns 200 without ever touching the
        // constraint. This one forces the losing branch deterministically -
        // a policy already exists for the quote while accepted_at is still
        // null, so the pre-check misses, the insert hits
        // uq_policies_quote_id, and the recovery read must turn that into
        // the same 200 (AD-5). Delete the catch in QuoteAcceptanceService
        // and this test fails; the concurrent one may not.
        String token = clientToken();
        UUID quoteId = createQuote(token);
        UUID existingPolicyId = insertPolicyDirectly(quoteId, "MI-2026-90000001");

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", "CA1234BM", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractId(response.getBody())).isEqualTo(existingPolicyId);
        assertThat(extract(response.getBody(), "policyNumber")).isEqualTo("MI-2026-90000001");
        assertThat(extract(response.getBody(), "holderName")).isEqualTo("Pre-existing Holder");
        assertThat(policyCount(quoteId)).isEqualTo(1);
        // The loser's whole transaction rolled back, its accepted_at write
        // included - in a real race the winner's own write is what stands.
        assertThat(acceptedAt(quoteId)).isNull();
    }

    @Test
    void clientRole_acceptAnotherCustomersQuote_returnsNotFoundNotForbidden() {
        String ownerToken = clientToken();
        String otherToken = clientToken();
        UUID quoteId = createQuote(ownerToken);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", "CA1234BM", null), otherToken);

        // 404, never 403: the response must not confirm the id is real (AD-10).
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"QUOTE_NOT_FOUND\"");
        assertThat(policyCount(quoteId)).isZero();
        assertThat(acceptedAt(quoteId)).isNull();
    }

    @Test
    void clientRole_acceptNonexistentQuote_returnsNotFound() {
        ResponseEntity<String> response = accept(
                UUID.randomUUID(), acceptBody(today(), "Ivan Petrov", "CA1234BM", null), clientToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"QUOTE_NOT_FOUND\"");
    }

    @Test
    void clientRole_acceptExpiredQuote_isConflictAndLeavesNothingBehind() {
        String token = clientToken();
        UUID quoteId = createQuote(token);
        // Only reachable by moving valid_until into the past: the endpoint
        // that creates a quote always dates it 14 days out.
        jdbcTemplate.update("UPDATE quotes SET valid_until = ? WHERE id = ?", today().minusDays(1), quoteId);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", "CA1234BM", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("\"code\":\"QUOTE_EXPIRED\"");
        assertThat(policyCount(quoteId)).isZero();
        assertThat(acceptedAt(quoteId)).isNull();
    }

    @Test
    void clientRole_acceptOnTheValidUntilDateItself_isStillAccepted() {
        String token = clientToken();
        UUID quoteId = createQuote(token);
        // The expiry boundary is inclusive (AD-6): valid *through* the day
        // itself, expired only from the day after.
        jdbcTemplate.update("UPDATE quotes SET valid_until = ? WHERE id = ?", today(), quoteId);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", "CA1234BM", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void clientRole_coverageStartInThePast_isFieldLevelErrorAndLeavesNothingBehind() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today().minusDays(1), "Ivan Petrov", "CA1234BM", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"QUOTE_COVERAGE_START_IN_PAST\"");
        assertThat(response.getBody()).contains("\"field\":\"coverageStart\"");
        assertThat(policyCount(quoteId)).isZero();
        assertThat(acceptedAt(quoteId)).isNull();
    }

    @Test
    void clientRole_coverageStartToday_isAccepted_boundaryInclusive() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", "CA1234BM", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void clientRole_noVehicleIdentifier_isFieldLevelError() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        ResponseEntity<String> response = accept(quoteId, acceptBody(today(), "Ivan Petrov", null, null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"QUOTE_VEHICLE_IDENTIFIER_REQUIRED\"");
        assertThat(response.getBody()).contains("\"field\":\"vehicleRegistration\"");
        assertThat(policyCount(quoteId)).isZero();
    }

    @Test
    void clientRole_blankVehicleIdentifiers_countAsAbsentNotAsMalformed() {
        String token = clientToken();
        UUID quoteId = createQuote(token);
        String body =
                "{\"coverageStart\":\"%s\",\"holderName\":\"Ivan Petrov\",\"vehicleRegistration\":\"\",\"vehicleVin\":\"\"}"
                        .formatted(today());

        ResponseEntity<String> response = accept(quoteId, body, token);

        // A form submitting the field it did not use as "" must get the
        // specific "provide one of these" message, not a pattern failure.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"QUOTE_VEHICLE_IDENTIFIER_REQUIRED\"");
    }

    @Test
    void clientRole_bothVehicleIdentifiers_isFieldLevelError() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", "CA1234BM", "WDB1234567N123456"), token);

        // Exactly one, not at least one: two identities on one contract
        // would leave nothing saying which governs.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"QUOTE_VEHICLE_IDENTIFIER_REQUIRED\"");
        assertThat(policyCount(quoteId)).isZero();
    }

    @Test
    void clientRole_blankHolderName_isRejectedByRequestValidation() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        ResponseEntity<String> response = accept(quoteId, acceptBody(today(), "   ", "CA1234BM", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"holderName\"");
    }

    @Test
    void clientRole_malformedVin_isRejectedByRequestValidation() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        // 16 characters, and containing a letter no VIN may use (I).
        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", null, "WDBI234567N12345"), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"vehicleVin\"");
    }

    @Test
    void clientRole_overlongRegistration_isRejectedByRequestValidation() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        ResponseEntity<String> response =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", "CA1234BM-CA1234BM-XX", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"vehicleRegistration\"");
    }

    @Test
    void missingCoverageStart_isRejectedByRequestValidation() {
        String token = clientToken();
        UUID quoteId = createQuote(token);
        String body = "{\"holderName\":\"Ivan Petrov\",\"vehicleRegistration\":\"CA1234BM\"}";

        ResponseEntity<String> response = accept(quoteId, body, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"coverageStart\"");
    }

    @Test
    void noToken_onAccept_isRejectedUnauthenticated() {
        ResponseEntity<String> response =
                accept(UUID.randomUUID(), acceptBody(today(), "Ivan Petrov", "CA1234BM", null), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
    }

    @Test
    void nonClientRole_onAccept_isRejectedForbidden() {
        String agentToken = jwtService.issueToken(UUID.randomUUID(), Role.AGENT);

        ResponseEntity<String> response =
                accept(UUID.randomUUID(), acceptBody(today(), "Ivan Petrov", "CA1234BM", null), agentToken);

        // 403 stays reserved for a role mismatch, which is a different
        // failure from "not yours" (AD-10, M1 AD-4).
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
    }

    @Test
    void issuedPolicy_survivesTheTariffChangingAndItsQuoteDisappearing() {
        String token = clientToken();
        UUID quoteId = createQuote(token);
        ResponseEntity<String> issued =
                accept(quoteId, acceptBody(today(), "Ivan Petrov", "CA1234BM", null), token);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID policyId = extractId(issued.getBody());

        try {
            // A policy copies, it never references (AD-4): neither a later
            // tariff change nor the disappearance of the quote it came from
            // may alter a figure on an issued contract.
            // Scoped to the one band this quote priced against (zone 1,
            // 1301-2100cc), not the whole table: a JVM death between the
            // update and the restore must not leave every other test in
            // this container quoting from a skewed tariff.
            jdbcTemplate.update(
                    "UPDATE tariff_rate SET base_premium = base_premium + 100 WHERE zone_id = 1 AND engine_cc_min = 1301");
            jdbcTemplate.update("DELETE FROM quotes WHERE id = ?", quoteId);

            assertThat(policyDecimal(policyId, "base_premium")).isEqualByComparingTo(new BigDecimal("141.12"));
            assertThat(policyDecimal(policyId, "age_surcharge")).isEqualByComparingTo(new BigDecimal("36.00"));
            assertThat(policyDecimal(policyId, "one_time_premium")).isEqualByComparingTo(new BigDecimal("177.12"));
            assertThat(policyDecimal(policyId, "total_premium")).isEqualByComparingTo(new BigDecimal("179.12"));
            assertThat(policyDecimal(policyId, "installment_amount")).isEqualByComparingTo(new BigDecimal("89.56"));
            assertThat(policyDecimal(policyId, "bonus_malus_factor")).isEqualByComparingTo(new BigDecimal("1.000"));
            assertThat(policyCount(quoteId)).isEqualTo(1);
        } finally {
            jdbcTemplate.update(
                    "UPDATE tariff_rate SET base_premium = base_premium - 100 WHERE zone_id = 1 AND engine_cc_min = 1301");
        }
    }

    // --- helpers ---

    private ResponseEntity<String> awaitThenAccept(
            CountDownLatch release, UUID quoteId, String body, String token) {
        try {
            release.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        return accept(quoteId, body, token);
    }

    private static LocalDate today() {
        return LocalDate.now(SOFIA_ZONE);
    }

    private static String acceptBody(LocalDate coverageStart, String holderName, String registration, String vin) {
        StringBuilder json = new StringBuilder("{\"coverageStart\":\"%s\",\"holderName\":\"%s\""
                .formatted(coverageStart, holderName));
        if (registration != null) {
            json.append(",\"vehicleRegistration\":\"%s\"".formatted(registration));
        }
        if (vin != null) {
            json.append(",\"vehicleVin\":\"%s\"".formatted(vin));
        }
        return json.append('}').toString();
    }

    /**
     * Plants a policy for a quote behind the application's back, so the
     * uncontended path's pre-check misses it and the insert has to lose to
     * {@code uq_policies_quote_id}. The figures mirror the worked example
     * the quote itself carries; only the holder name and number differ, so
     * the response can be told apart from a freshly-issued one.
     */
    private UUID insertPolicyDirectly(UUID quoteId, String policyNumber) {
        UUID policyId = UUID.randomUUID();
        UUID customerId = jdbcTemplate.queryForObject(
                "SELECT customer_id FROM quotes WHERE id = ?", UUID.class, quoteId);
        jdbcTemplate.update(
                """
                INSERT INTO policies (id, customer_id, quote_id, policy_number, holder_name,
                    vehicle_registration, vehicle_vin, coverage_start, coverage_end, issued_at,
                    driver_age, region_code, engine_cc, zone_id, zone_name, base_premium, age_surcharge,
                    bonus_malus_code, bonus_malus_factor, one_time_premium, installments, installment_fee,
                    total_premium, installment_amount, currency)
                VALUES (?, ?, ?, ?, 'Pre-existing Holder', 'CA0000AA', NULL, ?, ?, now(),
                    20, 'KH', 1500, 1, 'Zone 1', 141.12, 36.00, 'NEUTRAL', 1.000, 177.12, 2, 2.00,
                    179.12, 89.56, 'EUR')
                """,
                policyId,
                customerId,
                quoteId,
                policyNumber,
                today(),
                today().plusMonths(coverageMonths).minusDays(1));
        return policyId;
    }

    private int policyCount(UUID quoteId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM policies WHERE quote_id = ?", Integer.class, quoteId);
        return count == null ? 0 : count;
    }

    private BigDecimal policyDecimal(UUID policyId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM policies WHERE id = ?", BigDecimal.class, policyId);
    }

    private String acceptedAt(UUID quoteId) {
        return jdbcTemplate.queryForObject(
                "SELECT accepted_at::text FROM quotes WHERE id = ?", String.class, quoteId);
    }

    /** Registers a real client (quotes.customer_id is a FK) and mints its token. */
    private String clientToken() {
        String email = "accept-test-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<String> response = client().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email))
                .exchange(this::toEntity);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jwtService.issueToken(extractId(response.getBody()), Role.CLIENT);
    }

    /** The PRD addendum's worked example: KH / age 20 / 1500cc / 2 installments. */
    private UUID createQuote(String token) {
        ResponseEntity<String> response = client().post()
                .uri(QUOTES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(
                        "{\"driverAge\":20,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2,\"bonusMalusClass\":\"NEUTRAL\"}")
                .exchange(this::toEntity);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return extractId(response.getBody());
    }

    private ResponseEntity<String> accept(UUID quoteId, String body, String token) {
        RestClient.RequestBodySpec spec = client().post()
                .uri(QUOTES_PATH + "/" + quoteId + "/accept")
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return spec.body(body).exchange(this::toEntity);
    }

    private ResponseEntity<String> getJson(String path, String token) {
        return client().get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange(this::toEntity);
    }

    private static UUID extractId(String responseBody) {
        return UUID.fromString(extract(responseBody, "id"));
    }

    private static String extract(String responseBody, String field) {
        Matcher matcher =
                Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(responseBody);
        assertThat(matcher.find())
                .as("response body should carry a \"%s\" field: %s", field, responseBody)
                .isTrue();
        return matcher.group(1);
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
