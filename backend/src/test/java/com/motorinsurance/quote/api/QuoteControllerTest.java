package com.motorinsurance.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.motorinsurance.auth.application.JwtService;
import com.motorinsurance.auth.domain.Role;
import java.io.IOException;
import java.util.UUID;
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
 * Full-stack HTTP proof for {@code POST /api/v1/quotes} (Story 1.5) - the
 * first real consumer of Story 1.4's shared JWT gate, following the same
 * {@code RestClient} + random-port pattern as
 * {@code auth.config.JwtAuthenticationFilterTest}. A real Postgres
 * (Testcontainers) backs it, same rationale as {@link
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
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);

        ResponseEntity<String> response = postJson(validRequestBody(), clientToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"zoneName\":\"Zone 1\"");
        assertThat(response.getBody()).contains("\"totalPremium\":179.12");
        assertThat(response.getBody()).contains("\"installmentAmount\":89.56");
    }

    @Test
    void clientRole_regionCodeLowercase_isNormalizedAndStillSucceeds() {
        String clientToken = jwtService.issueToken(UUID.randomUUID(), Role.CLIENT);
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

    private String validRequestBody() {
        return "{\"driverAge\":20,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2}";
    }

    private ResponseEntity<String> postJson(String jsonBody, String bearerToken) {
        RestClient.RequestBodySpec spec =
                client().post().uri(QUOTES_PATH).contentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return spec.body(jsonBody).exchange(this::toEntity);
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
