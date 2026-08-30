package com.motorinsurance.auth.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Plain unit coverage for {@link DeploymentSecretsGuard} - no Spring context,
 * matching {@code JwtServiceTest}'s style. The {@code @Profile("docker")}
 * annotation itself (only active when the guard's bean would actually be
 * created) is not exercised here; this test only covers the fail-fast logic
 * once the bean exists.
 */
class DeploymentSecretsGuardTest {

    @Test
    void failFastOnInsecureJwtSecret_applicationYmlDefault_throws() {
        DeploymentSecretsGuard guard = new DeploymentSecretsGuard(DeploymentSecretsGuard.APPLICATION_YML_DEFAULT_SECRET);

        assertThatThrownBy(guard::failFastOnInsecureJwtSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void failFastOnInsecureJwtSecret_envExamplePlaceholder_throws() {
        DeploymentSecretsGuard guard = new DeploymentSecretsGuard(DeploymentSecretsGuard.ENV_EXAMPLE_PLACEHOLDER_SECRET);

        assertThatThrownBy(guard::failFastOnInsecureJwtSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void failFastOnInsecureJwtSecret_realSecret_doesNotThrow() {
        DeploymentSecretsGuard guard =
                new DeploymentSecretsGuard("a-real-generated-secret-at-least-32-bytes-long-for-hs256");

        assertThatCode(guard::failFastOnInsecureJwtSecret).doesNotThrowAnyException();
    }
}
