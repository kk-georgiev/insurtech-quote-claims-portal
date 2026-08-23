package com.motorinsurance.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Base Spring config (AD-1 shared scaffolding): allows the Vite dev server
 * to call this API directly from the browser during local development, so
 * the frontend/backend health round-trip (Story 1.1 AC) actually works
 * cross-origin (frontend on :5173, backend on :8080).
 *
 * <p>Dev-only allowlist of Vite's default ports, read from the single
 * {@code app.dev-cors-origins} property in {@code application.yml} - the
 * same property also feeds {@code management.endpoints.web.cors} for
 * Actuator endpoints, which are dispatched separately and never go through
 * this {@link WebMvcConfigurer}. One property, not two hardcoded lists that
 * can drift apart.
 *
 * <p>Revisit once the frontend is served from Docker Compose (Epic 4) or a
 * real deployed origin.
 */
@Configuration
public class CorsConfig {

    @Value("${app.dev-cors-origins}")
    private String[] devOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(devOrigins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
            }
        };
    }
}
