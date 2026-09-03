package com.motorinsurance.claim.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.motorinsurance.auth.application.JwtService;
import com.motorinsurance.auth.domain.Role;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Full-stack HTTP proof for {@code POST /api/v1/claims} (Story 10.2,
 * FR-M4-04) against a real Postgres, mirroring {@code
 * policy.api.PolicyControllerTest}'s pattern: policies are created the way a
 * client creates them (register, quote, accept) rather than inserted, and a
 * policy whose coverage has already ended is reached the same way that test
 * reaches one - moving its dates directly with the {@link JdbcTemplate},
 * since nothing in the API can accept a quote into the past.
 *
 * <p>The one scenario this class cannot reach - a DB failure after a photo
 * is already stored - is covered separately by {@code
 * claim.application.ClaimSubmissionServiceTest} with mocked collaborators;
 * forcing a real unique-constraint violation deterministically through a
 * shared Postgres sequence here would be fragile.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class ClaimControllerTest {

    private static final String CLAIMS_PATH = "/api/v1/claims";
    private static final String QUOTES_PATH = "/api/v1/quotes";
    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");
    private static final String VALID_DESCRIPTION = "The other driver ran a red light and hit my rear bumper.";
    private static final String VALID_LOCATION = "Sofia, near Orlov Most";

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
    void clientRole_ownPolicyWithPhotos_returns201WithClaimNumberAndAttachments() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());

        ResponseEntity<String> response =
                submitClaim(token, policyId, today(), VALID_DESCRIPTION, VALID_LOCATION, jpeg(16), png(16));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();
        assertThat(body).containsPattern("\"claimNumber\":\"CL-\\d{4}-\\d{8}\"");
        assertThat(body).contains("\"status\":\"SUBMITTED\"");
        assertThat(body).contains("\"description\":\"" + VALID_DESCRIPTION + "\"");
        assertThat(body).contains("\"contentType\":\"image/jpeg\"");
        assertThat(body).contains("\"contentType\":\"image/png\"");
    }

    @Test
    void clientRole_noPhotos_returns201WithEmptyAttachments() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());

        ResponseEntity<String> response = submitClaim(token, policyId, today(), VALID_DESCRIPTION, VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"attachments\":[]");
    }

    @Test
    void clientRole_anotherCustomersPolicy_returnsNotFoundNotForbidden() {
        String ownerToken = clientToken();
        String otherToken = clientToken();
        UUID policyId = issuePolicy(ownerToken, today());

        ResponseEntity<String> response = submitClaim(otherToken, policyId, today(), VALID_DESCRIPTION, VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"POLICY_NOT_FOUND\"");
    }

    @Test
    void incidentBeforeCoverageStart_isRejectedAsOutsideCoverage() {
        String token = clientToken();
        LocalDate coverageStart = today();
        UUID policyId = issuePolicy(token, coverageStart);

        ResponseEntity<String> response =
                submitClaim(token, policyId, coverageStart.minusDays(1), VALID_DESCRIPTION, VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("\"code\":\"CLAIM_INCIDENT_OUTSIDE_COVERAGE\"");
    }

    @Test
    void incidentAfterCoverageEnd_isRejectedAsOutsideCoverage() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());
        // Coverage is moved into the past (mirroring the EXPIRED-policy test
        // below) so "one day after coverageEnd" lands in the past too - a
        // policy issued today has a coverageEnd about a year out, and "one
        // day after" that would otherwise collide with the future-date rule
        // instead of exercising the coverage-window check this test targets.
        LocalDate coverageEnd = moveCoverageIntoThePast(policyId);

        ResponseEntity<String> response =
                submitClaim(token, policyId, coverageEnd.plusDays(1), VALID_DESCRIPTION, VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("\"code\":\"CLAIM_INCIDENT_OUTSIDE_COVERAGE\"");
    }

    @Test
    void incidentExactlyOnCoverageEnd_isAccepted_boundaryInclusive() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());
        LocalDate coverageEnd = moveCoverageIntoThePast(policyId);

        ResponseEntity<String> response = submitClaim(token, policyId, coverageEnd, VALID_DESCRIPTION, VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void incidentInsideCoverage_onAnAlreadyExpiredPolicy_isStillAccepted() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());
        LocalDate originalIncidentDate = today();
        // Unreachable through the API - a quote can only be accepted into
        // the present or future - so the row's dates are moved directly,
        // mirroring PolicyControllerTest's identical technique.
        jdbcTemplate.update(
                "UPDATE policies SET coverage_start = ?, coverage_end = ? WHERE id = ?",
                originalIncidentDate.minusYears(2),
                originalIncidentDate.plusYears(1).minusDays(1),
                policyId);

        ResponseEntity<String> response =
                submitClaim(token, policyId, originalIncidentDate, VALID_DESCRIPTION, VALID_LOCATION);

        // Coverage is checked as of the incident date, not "is the policy
        // active now" (FR-M4-05) - an EXPIRED policy still accepts a claim
        // for an incident that fell inside its coverage window.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void futureIncidentDate_isRejectedWithAFieldError() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());

        ResponseEntity<String> response =
                submitClaim(token, policyId, today().plusDays(1), VALID_DESCRIPTION, VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"CLAIM_INCIDENT_DATE_IN_FUTURE\"");
        assertThat(response.getBody()).contains("\"field\":\"incidentDate\"");
    }

    @Test
    void descriptionTooShort_isRejectedWithAFieldError() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());

        ResponseEntity<String> response = submitClaim(token, policyId, today(), "too short", VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"description\"");
    }

    @Test
    void descriptionTooLong_isRejectedWithAFieldError() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());
        String tooLong = "a".repeat(2001);

        ResponseEntity<String> response = submitClaim(token, policyId, today(), tooLong, VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"SHARED_VALIDATION_ERROR\"");
        assertThat(response.getBody()).contains("\"field\":\"description\"");
    }

    @Test
    void oneUnsupportedFileInAnOtherwiseValidBatch_writesNothing() {
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());
        Integer claimsBefore = countClaims(policyId);

        ResponseEntity<String> response =
                submitClaim(token, policyId, today(), VALID_DESCRIPTION, VALID_LOCATION, jpeg(16), pdf());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"ATTACHMENT_UNSUPPORTED_TYPE\"");
        // The whole batch fails before anything is written (M4-AD-3) - no
        // claim row exists for this policy afterwards.
        assertThat(countClaims(policyId)).isEqualTo(claimsBefore);
    }

    @Test
    void fileOverSpringsOwnMultipartCap_isACleanBadRequestNotAnOpaqueServerError() {
        // application.yml sets spring.servlet.multipart.max-file-size to
        // 10MB, deliberately above storage.attachment.max-file-size-bytes
        // (5MiB) - this file is over BOTH, but its size only matters for
        // proving the REAL Spring-level cliff (Story 10.1 deferred finding):
        // Spring rejects it during multipart resolution, before
        // AttachmentValidator - whose own, lower cap - ever runs.
        String token = clientToken();
        UUID policyId = issuePolicy(token, today());
        byte[] overSpringsCap = jpeg(11 * 1024 * 1024);

        ResponseEntity<String> response =
                submitClaim(token, policyId, today(), VALID_DESCRIPTION, VALID_LOCATION, overSpringsCap);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"ATTACHMENT_TOO_LARGE\"");
    }

    @Test
    void noToken_isRejectedUnauthenticated() {
        UUID policyId = issuePolicy(clientToken(), today());

        ResponseEntity<String> response = submitClaim(null, policyId, today(), VALID_DESCRIPTION, VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"");
    }

    @Test
    void nonClientRole_isRejectedForbidden() {
        String agentToken = jwtService.issueToken(UUID.randomUUID(), Role.AGENT);
        UUID policyId = issuePolicy(clientToken(), today());

        ResponseEntity<String> response = submitClaim(agentToken, policyId, today(), VALID_DESCRIPTION, VALID_LOCATION);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
    }

    // --- helpers ---

    private static LocalDate today() {
        return LocalDate.now(SOFIA_ZONE);
    }

    private UUID issuePolicy(String token, LocalDate coverageStart) {
        return acceptQuote(createQuote(token), token, coverageStart);
    }

    /**
     * Moves an issued policy's coverage window into the recent past and
     * returns its new {@code coverageEnd} - unreachable through the API
     * itself (a quote can only be accepted into the present or future), so
     * the row is updated directly, mirroring {@code
     * PolicyControllerTest.clientRole_coverAlreadyEnded_readsAsExpired}'s
     * identical technique. Keeps every boundary date in the past, so these
     * tests exercise the coverage-window check in isolation rather than
     * tripping the future-incident-date rule as a side effect.
     */
    private LocalDate moveCoverageIntoThePast(UUID policyId) {
        LocalDate coverageStart = today().minusDays(60);
        LocalDate coverageEnd = today().minusDays(10);
        jdbcTemplate.update(
                "UPDATE policies SET coverage_start = ?, coverage_end = ? WHERE id = ?", coverageStart, coverageEnd, policyId);
        return coverageEnd;
    }

    private Integer countClaims(UUID policyId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM claims WHERE policy_id = ?", Integer.class, policyId);
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
        String email = "claim-test-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<String> response = client().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email))
                .exchange(this::toEntity);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jwtService.issueToken(extractId(response.getBody()), Role.CLIENT);
    }

    /**
     * Encodes the multipart body by hand rather than via {@code
     * MultipartBodyBuilder} - {@code RestClient}'s default multipart writer
     * pulls in a class with a hard bytecode reference to {@code
     * org.reactivestreams.Publisher}, which this project's pure servlet
     * stack (no WebFlux, no reactor-core) does not have on its classpath.
     * Sending the body as a plain {@code byte[]} with a hand-set {@code
     * Content-Type} header sidesteps that converter entirely.
     */
    private static final String BOUNDARY = "ClaimControllerTestBoundary7f3a9c";

    private ResponseEntity<String> submitClaim(
            String token, UUID policyId, LocalDate incidentDate, String description, String location, byte[]... photos) {
        byte[] body = multipartBody(policyId, incidentDate, description, location, photos);

        RestClient.RequestBodySpec spec = client().post()
                .uri(CLAIMS_PATH)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + BOUNDARY);
        if (token != null) {
            spec = (RestClient.RequestBodySpec) spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return spec.body(body).exchange(this::toEntity);
    }

    private static byte[] multipartBody(
            UUID policyId, LocalDate incidentDate, String description, String location, byte[]... photos) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeField(out, "policyId", policyId.toString());
        writeField(out, "incidentDate", incidentDate.toString());
        writeField(out, "description", description);
        writeField(out, "location", location);
        for (int i = 0; i < photos.length; i++) {
            writeFile(out, "attachments", "photo" + i + ".jpg", "image/jpeg", photos[i]);
        }
        writeUtf8(out, "--" + BOUNDARY + "--\r\n");
        return out.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream out, String name, String value) {
        writeUtf8(out, "--" + BOUNDARY + "\r\n");
        writeUtf8(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeUtf8(out, value + "\r\n");
    }

    private static void writeFile(ByteArrayOutputStream out, String name, String filename, String contentType, byte[] content) {
        writeUtf8(out, "--" + BOUNDARY + "\r\n");
        writeUtf8(out, "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n");
        writeUtf8(out, "Content-Type: " + contentType + "\r\n\r\n");
        try {
            out.write(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        writeUtf8(out, "\r\n");
    }

    private static void writeUtf8(ByteArrayOutputStream out, String text) {
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    private static byte[] jpeg(int totalLength) {
        return withHeader(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, totalLength);
    }

    private static byte[] png(int totalLength) {
        return withHeader(
                new byte[] {
                    (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                    (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
                },
                totalLength);
    }

    private static byte[] pdf() {
        return "%PDF-1.7 definitely not an image".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] withHeader(byte[] header, int totalLength) {
        byte[] file = new byte[Math.max(totalLength, header.length)];
        System.arraycopy(header, 0, file, 0, header.length);
        return file;
    }
}
