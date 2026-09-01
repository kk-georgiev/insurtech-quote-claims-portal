package com.motorinsurance.policy.api;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack HTTP proof for {@code GET /api/v1/policies} and
 * {@code /{id}} (Story 8.3, FR-M3-10) against a real Postgres.
 *
 * <p>Policies are created the way a client creates them - register,
 * calculate a quote, accept it - rather than inserted, so what these reads
 * return is what the acceptance path actually wrote. The one state HTTP
 * cannot reach is a policy whose cover has already ended: nothing can
 * accept a quote into the past, so that row's dates are moved with the
 * {@link JdbcTemplate}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class PolicyControllerTest {

    private static final String POLICIES_PATH = "/api/v1/policies";
    private static final String QUOTES_PATH = "/api/v1/quotes";
    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void clientRole_listPolicies_returnsOnlyOwnPoliciesNewestFirst() {
        String ownerToken = clientToken();
        String otherToken = clientToken();
        UUID first = issuePolicy(ownerToken, today());
        UUID second = issuePolicy(ownerToken, today());
        // Belongs to a different customer - must never appear in the
        // owner's list, under any parameter (AD-10).
        issuePolicy(otherToken, today());

        ResponseEntity<String> response = get(POLICIES_PATH, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // A bare JSON array (AD-12) - not an envelope object.
        assertThat(response.getBody()).startsWith("[").endsWith("]");
        assertThat(extractAllIds(response.getBody())).containsExactly(second, first);
    }

    @Test
    void clientRole_listPolicies_noneYet_returnsEmptyArrayNotError() {
        ResponseEntity<String> response = get(POLICIES_PATH, clientToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    void clientRole_policyDetail_carriesTheFullIssuedBreakdown() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());

        ResponseEntity<String> response = get(POLICIES_PATH + "/" + policyId, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).containsPattern("\"policyNumber\":\"MI-\\d{4}-\\d{8}\"");
        assertThat(extract(body, "coverageStart")).isEqualTo(today().toString());
        assertThat(extract(body, "holderName")).isEqualTo("Ivan Petrov");
        assertThat(extract(body, "vehicleRegistration")).isEqualTo("CA1234BM");
        // The same figures the quote carried - a client comparing the two
        // must see they match (FR-M3-07/FR-M3-10).
        assertThat(body).contains("\"basePremium\":141.12");
        assertThat(body).contains("\"ageSurcharge\":36.00");
        assertThat(body).contains("\"bonusMalusFactor\":1.000");
        assertThat(body).contains("\"totalPremium\":179.12");
        assertThat(body).contains("\"currency\":\"EUR\"");
    }

    @Test
    void clientRole_listAndDetail_returnTheSameShape() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());

        String fromList = get(POLICIES_PATH, token).getBody();
        String fromDetail = get(POLICIES_PATH + "/" + policyId, token).getBody();

        // AD-12: the list returns the same DTO the detail endpoint does, so
        // the single-element list body is the detail body inside brackets.
        assertThat(fromList).isEqualTo("[" + fromDetail + "]");
    }

    @Test
    void clientRole_coverStartingToday_readsAsActive() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());

        // Inclusive boundary (AD-6): cover from today is already active.
        assertThat(get(POLICIES_PATH + "/" + policyId, token).getBody()).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void clientRole_coverStartingLater_readsAsScheduled() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today().plusDays(30));

        assertThat(get(POLICIES_PATH + "/" + policyId, token).getBody()).contains("\"status\":\"SCHEDULED\"");
    }

    @Test
    void clientRole_coverAlreadyEnded_readsAsExpired() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());
        // Unreachable through the API - a quote can only be accepted into
        // the present or future, so the row's dates are moved directly.
        jdbcTemplate.update(
                "UPDATE policies SET coverage_start = ?, coverage_end = ? WHERE id = ?",
                today().minusYears(2),
                today().minusDays(1),
                policyId);

        assertThat(get(POLICIES_PATH + "/" + policyId, token).getBody()).contains("\"status\":\"EXPIRED\"");
    }

    @Test
    void clientRole_anotherCustomersPolicy_returnsNotFoundNotForbidden() {
        String ownerToken = clientToken();
        String otherToken = clientToken();
        UUID policyId = issuePolicy(ownerToken, today());

        ResponseEntity<String> response = get(POLICIES_PATH + "/" + policyId, otherToken);

        // Never 403: the response must not confirm the id belongs to
        // someone else (AD-10).
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"POLICY_NOT_FOUND\"");
    }

    @Test
    void clientRole_unknownPolicyId_returnsNotFound() {
        ResponseEntity<String> response = get(POLICIES_PATH + "/" + UUID.randomUUID(), clientToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"POLICY_NOT_FOUND\"");
    }

    @Test
    void clientRole_malformedPolicyId_returnsFieldLevelValidationError() {
        ResponseEntity<String> response = get(POLICIES_PATH + "/not-a-uuid", clientToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"id\"");
    }

    @Test
    void noToken_onListOrDetail_isRejectedUnauthenticated() {
        for (String path : new String[] {POLICIES_PATH, POLICIES_PATH + "/" + UUID.randomUUID()}) {
            ResponseEntity<String> response = get(path, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
        }
    }

    @Test
    void nonClientRole_onListOrDetail_isRejectedForbidden() {
        String agentToken = jwtService.issueToken(UUID.randomUUID(), Role.AGENT);

        for (String path : new String[] {POLICIES_PATH, POLICIES_PATH + "/" + UUID.randomUUID()}) {
            ResponseEntity<String> response = get(path, agentToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
        }
    }

    @Test
    void acceptedQuote_carriesTheIdOfThePolicyItProduced() {
        String token = clientToken();
        UUID quoteId = createQuote(token);
        UUID policyId = acceptQuote(quoteId, token, today());

        // Story 8.3's link (AD-13): the quote's own screen can reach the
        // contract it produced without fetching every policy to match on.
        assertThat(extract(get(QUOTES_PATH + "/" + quoteId, token).getBody(), "policyId"))
                .isEqualTo(policyId.toString());
        assertThat(get(QUOTES_PATH, token).getBody()).contains("\"policyId\":\"" + policyId + "\"");
    }

    @Test
    void unacceptedQuote_carriesNoPolicyId() {
        String token = clientToken();
        UUID quoteId = createQuote(token);

        assertThat(get(QUOTES_PATH + "/" + quoteId, token).getBody()).contains("\"policyId\":null");
    }

    // --- helpers ---

    private static LocalDate today() {
        return LocalDate.now(SOFIA_ZONE);
    }

    private UUID issuePolicy(String token, LocalDate coverageStart) {
        return acceptQuote(createQuote(token), token, coverageStart);
    }

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

    private UUID acceptQuote(UUID quoteId, String token, LocalDate coverageStart) {
        ResponseEntity<String> response = client().post()
                .uri(QUOTES_PATH + "/" + quoteId + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(
                        "{\"coverageStart\":\"%s\",\"holderName\":\"Ivan Petrov\",\"vehicleRegistration\":\"CA1234BM\"}"
                                .formatted(coverageStart))
                .exchange(this::toEntity);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return extractId(response.getBody());
    }

    private String clientToken() {
        String email = "policy-test-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<String> response = client().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email))
                .exchange(this::toEntity);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jwtService.issueToken(extractId(response.getBody()), Role.CLIENT);
    }

    private ResponseEntity<String> get(String path, String bearerToken) {
        RestClient.RequestHeadersSpec<?> spec = client().get().uri(path);
        if (bearerToken != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return spec.exchange(this::toEntity);
    }

    private static UUID extractId(String responseBody) {
        return UUID.fromString(extract(responseBody, "id"));
    }

    private static String extract(String responseBody, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(responseBody);
        assertThat(matcher.find())
                .as("response body should carry a \"%s\" field: %s", field, responseBody)
                .isTrue();
        return matcher.group(1);
    }

    private static List<UUID> extractAllIds(String responseBody) {
        Matcher matcher = Pattern.compile("\\{\"id\":\"([0-9a-fA-F-]{36})\"").matcher(responseBody);
        List<UUID> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(UUID.fromString(matcher.group(1)));
        }
        return ids;
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
