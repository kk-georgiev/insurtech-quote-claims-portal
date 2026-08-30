package com.motorinsurance.auth.config;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails application startup fast when the {@code docker} profile is active
 * (activated by {@code docker-compose.yml}'s {@code backend} service, see
 * {@code SPRING_PROFILES_ACTIVE}) and {@code jwt.secret} still resolves to
 * one of the known insecure placeholder values shipped in this repo -
 * either {@code application.yml}'s own fallback, or the literal
 * {@code .env.example} tells a new developer to copy into {@code .env} and
 * replace.
 *
 * <p>Closes epic-1-retro-item-10 now that Epic 4's Docker Compose stack is
 * an actual deployment artifact - the "real deployment target/profile
 * strategy" that item was waiting on. Native {@code mvn spring-boot:run}
 * never activates the {@code docker} profile, so the insecure default
 * {@code application.yml} documents keeps working unchanged for local dev.
 *
 * <p>Deliberately narrow: this guards only {@code jwt.secret}. The sibling
 * Postgres-credentials and seeded-demo-account gaps noted alongside this
 * item in {@code deferred-work.md} are not addressed here - they are
 * separate action items that can reuse the {@code docker} profile this
 * class establishes, once someone picks them up.
 */
@Component
@Profile("docker")
public class DeploymentSecretsGuard {

    static final String APPLICATION_YML_DEFAULT_SECRET =
            "insecure-dev-only-default-jwt-secret-do-not-use-in-any-real-deployment-override-via-JWT_SECRET";

    static final String ENV_EXAMPLE_PLACEHOLDER_SECRET = "replace-with-a-long-random-secret-never-commit-a-real-one";

    private static final Set<String> KNOWN_INSECURE_SECRETS =
            Set.of(APPLICATION_YML_DEFAULT_SECRET, ENV_EXAMPLE_PLACEHOLDER_SECRET);

    private final String jwtSecret;

    public DeploymentSecretsGuard(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @PostConstruct
    void failFastOnInsecureJwtSecret() {
        if (KNOWN_INSECURE_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException(
                    "Refusing to start: JWT_SECRET is still the insecure placeholder from .env.example. "
                            + "Generate a real secret (e.g. `openssl rand -base64 48`), set it as JWT_SECRET "
                            + "in your .env file, and re-run `docker compose up --build`.");
        }
    }
}
