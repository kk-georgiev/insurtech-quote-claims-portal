package com.motorinsurance.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides the {@link PasswordEncoder} bean used to hash passwords at
 * registration (Story 1.2) and verify them at login (Story 1.3). Only
 * {@code spring-security-crypto} is on the classpath this story - not the
 * full {@code spring-boot-starter-security}, which would auto-secure every
 * endpoint (including Story 1.1's health check) before real auth exists.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
