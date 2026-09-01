package com.motorinsurance.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack proof for Story 9.1 (FR-M3-14): {@code GET /v3/api-docs} is
 * reachable with no auth header and lists all 8 existing endpoints. Same
 * {@code RestClient} + random-port + Testcontainers-Postgres pattern as
 * {@code auth.api.AuthControllerTest}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class OpenApiConfigTest {

    private static final String API_DOCS_PATH = "/v3/api-docs";
    private static final String SWAGGER_UI_PATH = "/swagger-ui/index.html";
    private static final String SWAGGER_UI_HTML_PATH = "/swagger-ui.html";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Test
    void apiDocs_noAuthHeader_returnsOkListingAllKnownEndpoints() {
        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://localhost:" + port + API_DOCS_PATH)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();

        // AuthController
        assertThat(body).contains("\"/api/v1/auth/register\"");
        assertThat(body).contains("\"/api/v1/auth/login\"");
        // QuoteController - calculate/list share a path key with different
        // verbs (POST/GET), and getById/accept share "/{id}" vs "/{id}/accept".
        assertThat(body).contains("\"/api/v1/quotes\"");
        assertThat(body).contains("\"/api/v1/quotes/{id}\"");
        assertThat(body).contains("\"/api/v1/quotes/{id}/accept\"");
        // PolicyController - list/getById share the same path-key pattern.
        assertThat(body).contains("\"/api/v1/policies\"");
        assertThat(body).contains("\"/api/v1/policies/{id}\"");

        // "with schemas" (not just bare paths): springdoc's components/schemas
        // section carries the real request/response DTOs, e.g. QuoteResponse
        // - proof the shapes came from reflection, not a hand-written stub.
        assertThat(body).contains("\"components\"");
        assertThat(body).contains("\"QuoteResponse\"");

        // Bonus-malus provenance disclaimer (NFR-8) - OpenApiConfig's Info
        // description, not inferable from the enum's Java name alone.
        assertThat(body).contains("not official or regulatorily determined Bulgarian market values");
    }

    @Test
    void swaggerUi_noAuthHeader_returnsOk() {
        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://localhost:" + port + SWAGGER_UI_PATH)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void swaggerUiHtml_noAuthHeader_returnsOk() {
        // springdoc's conventional entry point (/swagger-ui.html) 302s to
        // /swagger-ui/index.html rather than serving it directly, so this
        // RestClient is built with a redirect-following JDK HttpClient -
        // the default RestClient.create() JDK backend does NOT follow
        // redirects - to assert the final resolved status, not the
        // intermediate 3xx.
        RestClient followingRedirects = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()))
                .build();

        ResponseEntity<String> response = followingRedirects
                .get()
                .uri("http://localhost:" + port + SWAGGER_UI_HTML_PATH)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
