package com.motorinsurance.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides the {@link PasswordEncoder} bean used to hash passwords at
 * registration (Story 1.2) and verify them at login (Story 1.3).
 * {@code spring-security-crypto} was the only Security dependency on the
 * classpath at first (deliberately not the full
 * {@code spring-boot-starter-security}, which would have auto-secured every
 * endpoint - including Story 1.1's health check - before real auth existed);
 * Story 1.4 has since added the full starter (transitively including this
 * same crypto module) to wire up the real JWT filter chain.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
