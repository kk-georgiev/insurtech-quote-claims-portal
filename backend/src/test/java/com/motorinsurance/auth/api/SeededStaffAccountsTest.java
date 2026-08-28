package com.motorinsurance.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.motorinsurance.auth.application.JwtService;
import com.motorinsurance.auth.domain.Role;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.core.io.ClassPathResource;
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
 * Proof that {@code V5__seed_staff_accounts.sql} provisions working AGENT,
 * LIQUIDATOR and ADMINISTRATOR accounts (Story 2.1) - the accounts every
 * later Epic 2 story (role routing, shells, guards) needs something to log in
 * as, since self-registration hardcodes {@code Role.CLIENT}.
 *
 * <p>The point of the story is that the seeded rows work <em>by
 * construction</em>: no production class was touched, so every login here
 * goes through Story 1.3's unchanged {@code POST /api/v1/auth/login}. The
 * assertions therefore run against real HTTP, plus a {@link JdbcTemplate} for
 * the things HTTP cannot show - the exact row shape the migration produced,
 * and that no plaintext password reached the database. Querying via
 * {@code JdbcTemplate} deliberately avoids adding finder methods to
 * {@code UserRepository} that only a test would ever call.
 *
 * <p>Same inline {@code RestClient} + random-port + Testcontainers-Postgres
 * pattern as {@link AuthControllerTest} and {@code quote.api.QuoteControllerTest}
 * (each DB test class declares its own container; there is deliberately no
 * shared base class). Because this class owns its container outright, the
 * row-count assertions below can be absolute rather than filtered.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class SeededStaffAccountsTest {

    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String QUOTES_PATH = "/api/v1/quotes";

    // The one demo password all three share, exactly as README's "Demo
    // accounts" table documents it. Distinguishing the accounts by role
    // rather than by secret keeps the mentor demo friction-free.
    // readmeDocumentsTheWorkingDemoCredentials pins the README to this
    // constant, and the login tests prove the constant actually
    // authenticates - together they stop the table going stale.
    private static final String DEMO_PASSWORD = "DemoPass123!";

    /** One seeded row, as V5 literally writes it. */
    private record SeededAccount(String email, UUID id, Role role) {}

    private static final SeededAccount AGENT = new SeededAccount(
            "agent@motorinsurance.demo", UUID.fromString("bd8a03c5-0f35-4103-b864-c8ff728ea476"), Role.AGENT);
    private static final SeededAccount LIQUIDATOR = new SeededAccount(
            "liquidator@motorinsurance.demo", UUID.fromString("f20ac9c9-c211-4e19-a61d-06b236969437"), Role.LIQUIDATOR);
    private static final SeededAccount ADMINISTRATOR = new SeededAccount(
            "administrator@motorinsurance.demo",
            UUID.fromString("538a27f4-2e71-4c9e-b4f1-2a3f12d695e0"),
            Role.ADMINISTRATOR);
    private static final List<SeededAccount> STAFF_ACCOUNTS = List.of(AGENT, LIQUIDATOR, ADMINISTRATOR);

    private static final String MIGRATION_RESOURCE = "db/migration/V5__seed_staff_accounts.sql";
    // Surefire runs with the Maven module directory (backend/) as the working
    // directory, so the root README is one level up.
    private static final Path README = Path.of("..", "README.md");
    private static final String STAFF_ROLES_FILTER = "role IN ('AGENT', 'LIQUIDATOR', 'ADMINISTRATOR')";

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
    void seededAgent_logsInAndReceivesATokenForThatRow() {
        assertLoginIssuesTokenFor(AGENT, DEMO_PASSWORD);
    }

    @Test
    void seededLiquidator_logsInAndReceivesATokenForThatRow() {
        assertLoginIssuesTokenFor(LIQUIDATOR, DEMO_PASSWORD);
    }

    @Test
    void seededAdministrator_logsInAndReceivesATokenForThatRow() {
        assertLoginIssuesTokenFor(ADMINISTRATOR, DEMO_PASSWORD);
    }

    @Test
    void seededEmailMixedCase_stillLogsIn() {
        // The Emails.normalize applied at lookup is what lets a
        // mobile-keyboard-capitalized address reach the lower-cased row in
        // users.email's case-SENSITIVE UNIQUE column. That the seed itself is
        // canonical is pinned separately by
        // seededEmails_areStoredAlreadyNormalized - this test could not
        // detect a capitalized seed, since the plain-lowercase logins above
        // would fail first.
        assertLoginIssuesTokenFor(AGENT, "Agent@MotorInsurance.Demo", DEMO_PASSWORD);
    }

    @Test
    void seededAccount_wrongPassword_returnsUnauthorizedInvalidCredentials() {
        ResponseEntity<String> response =
                postJson(LOGIN_PATH, credentials(AGENT.email(), "not-the-demo-password"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_INVALID_CREDENTIALS\"");
        // Generic message only - a seeded account must be indistinguishable
        // from an unregistered one (AD-3, no user enumeration).
        assertThat(response.getBody()).doesNotContain(AGENT.email());
    }

    @Test
    void selfRegisteringASeededEmail_isRejectedAsEmailTaken() {
        // The users.email UNIQUE constraint covers seeded rows exactly as it
        // covers self-registered ones; nothing about the seed is privileged.
        ResponseEntity<String> response =
                postJson(REGISTER_PATH, credentials(ADMINISTRATOR.email(), "some-other-password"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_EMAIL_TAKEN\"");
    }

    @Test
    void everySeededStaffToken_onClientOnlyQuoteEndpoint_isRejectedForbidden() {
        // Real tokens from real logins of real seeded rows - not hand-minted
        // jwtService.issueToken calls like QuoteControllerTest uses - proving
        // Epic 1's @PreAuthorize("hasRole('CLIENT')") still governs now that
        // non-CLIENT accounts genuinely exist. All three roles, so no seeded
        // account can quietly acquire CLIENT authority.
        String quoteBody = "{\"driverAge\":20,\"regionCode\":\"KH\",\"engineCc\":1500,\"installments\":2}";

        for (SeededAccount account : STAFF_ACCOUNTS) {
            String staffToken = tokenFromLogin(account.email(), DEMO_PASSWORD);

            ResponseEntity<String> response = postJson(QUOTES_PATH, quoteBody, staffToken);

            assertThat(response.getStatusCode())
                    .as("%s must not be allowed to create a quote", account.role())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
        }
    }

    @Test
    void migratedDatabase_holdsExactlyOneRowPerStaffRoleAndNoClients() {
        assertThat(countWhere("role = 'AGENT'")).isEqualTo(1);
        assertThat(countWhere("role = 'LIQUIDATOR'")).isEqualTo(1);
        assertThat(countWhere("role = 'ADMINISTRATOR'")).isEqualTo(1);
        // "The CLIENT role is unaffected": V5 inserts no CLIENT row at all.
        // Unscoped, because this class owns its own Testcontainers database
        // and its only registration attempt is the one asserted to 409 - so
        // nothing else can add a CLIENT row here, and a future seed adding
        // one under any address must fail this.
        assertThat(countWhere("role = 'CLIENT'")).isZero();

        // The literal ids V5 writes, which nothing else pins - V2 gives `id`
        // no DEFAULT, so these are exactly the values in the migration file.
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE " + STAFF_ROLES_FILTER + " ORDER BY email", UUID.class);
        assertThat(ids)
                .containsExactlyInAnyOrder(AGENT.id(), LIQUIDATOR.id(), ADMINISTRATOR.id());
    }

    @Test
    void seededEmails_areStoredAlreadyNormalized() {
        // The migration-specific invariant: AuthenticationService looks up by
        // Emails.normalize(...) (trim + lower-case) against a case-sensitive
        // column, so a seed row stored any other way is an account nobody can
        // ever log into. Asserted against the stored value directly rather
        // than inferred from a successful login.
        assertThat(countWhere(STAFF_ROLES_FILTER + " AND email <> lower(btrim(email))"))
                .as("every seeded staff email must already be trimmed and lower-cased")
                .isZero();
    }

    @Test
    void seededPasswordHashes_areBcryptCostTenAndNeverPlaintext() {
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT password_hash FROM users WHERE " + STAFF_ROLES_FILTER + " ORDER BY email", String.class);

        assertThat(hashes).hasSize(3);
        assertThat(hashes).allSatisfy(hash -> {
            assertThat(hash).hasSize(60);
            assertThat(hash).startsWith("$2");
            // Cost 10, matching `new BCryptPasswordEncoder()` in
            // PasswordEncoderConfig: any cost would verify at login, but a
            // different one would mean the seed did not come from the app's
            // own encoder.
            assertThat(hash).matches("^\\$2[aby]\\$10\\$.{53}$");
            assertThat(hash).doesNotContain(DEMO_PASSWORD);
        });
        // Distinct salts, even though the plaintext is shared.
        assertThat(hashes).doesNotHaveDuplicates();
    }

    @Test
    void migrationFile_containsNoPlaintextPassword() throws IOException {
        // NFR-2: the migration - and therefore every database column - carries
        // hashes only. The plaintext lives in README.md and in this test
        // class, and nowhere else.
        String migrationSql = new ClassPathResource(MIGRATION_RESOURCE).getContentAsString(StandardCharsets.UTF_8);

        assertThat(migrationSql).doesNotContain(DEMO_PASSWORD);
        assertThat(migrationSql).contains(AGENT.email(), LIQUIDATOR.email(), ADMINISTRATOR.email());
    }

    @Test
    void readmeDocumentsTheWorkingDemoCredentials() throws IOException {
        // FR-4 is "the credentials are documented", so the documentation is
        // part of the deliverable and has to be pinned like anything else.
        // These are the same constants the login tests authenticate with, so
        // README and hashes cannot drift apart behind a green suite.
        assertThat(README)
                .as("root README should be one level up from the backend module")
                .exists();
        String readme = Files.readString(README, StandardCharsets.UTF_8);

        assertThat(readme).contains(DEMO_PASSWORD);
        for (SeededAccount account : STAFF_ACCOUNTS) {
            assertThat(readme)
                    .as("README should document the %s demo account", account.role())
                    .contains(account.email(), account.role().name());
        }
    }

    @Test
    void reapplyingTheSeedMigration_addsNoDuplicateRows() throws IOException {
        // Flyway never re-runs a migration it has already recorded, so the
        // untargeted ON CONFLICT DO NOTHING would go permanently unexercised
        // without this. It earns its place on a database seeded by some other
        // route - a restored dump, a hand-applied script, a squashed baseline -
        // where a second INSERT would otherwise trip users_email_key (or
        // users_pkey) and fail the startup migration. Re-applying is a genuine
        // no-op, so this leaves nothing behind for the sibling tests.
        assertThat(countWhere(STAFF_ROLES_FILTER)).isEqualTo(3);

        jdbcTemplate.execute(new ClassPathResource(MIGRATION_RESOURCE).getContentAsString(StandardCharsets.UTF_8));

        assertThat(countWhere(STAFF_ROLES_FILTER)).isEqualTo(3);
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE " + STAFF_ROLES_FILTER, UUID.class);
        assertThat(ids).containsExactlyInAnyOrder(AGENT.id(), LIQUIDATOR.id(), ADMINISTRATOR.id());
    }

    private void assertLoginIssuesTokenFor(SeededAccount account, String password) {
        assertLoginIssuesTokenFor(account, account.email(), password);
    }

    /**
     * Logs in with {@code emailAsTyped} and asserts the returned token carries
     * both the role <em>and</em> the id of {@code account}'s seeded row - the
     * same property {@code AuthControllerTest.registerThenLogin_issuesTokenCarryingThatUsersRealIdAndRole}
     * pins for self-registered users. Without the id check, a token minted for
     * the wrong seeded row would satisfy every other assertion here.
     */
    private void assertLoginIssuesTokenFor(SeededAccount account, String emailAsTyped, String password) {
        JwtService.ParsedToken parsed = jwtService.parseToken(tokenFromLogin(emailAsTyped, password));

        assertThat(parsed.role()).isEqualTo(account.role());
        assertThat(parsed.userId()).isEqualTo(account.id());
    }

    private String tokenFromLogin(String email, String password) {
        ResponseEntity<String> response = postJson(LOGIN_PATH, credentials(email, password), null);

        assertThat(response.getStatusCode())
                .as("login for %s should succeed: %s", email, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return extractToken(response.getBody());
    }

    private long countWhere(String predicate) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE " + predicate, Long.class);
        return count == null ? -1L : count;
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static String extractToken(String responseBody) {
        Matcher matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(responseBody);
        assertThat(matcher.find())
                .as("response body should contain a \"token\" field: %s", responseBody)
                .isTrue();
        return matcher.group(1);
    }

    private ResponseEntity<String> postJson(String path, String jsonBody, String bearerToken) {
        RestClient.RequestBodySpec spec =
                client().post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return spec.body(jsonBody).exchange(this::toEntity);
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
