package com.motorinsurance.shared.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Base Spring config (AD-1 shared scaffolding): allows the Vite dev server
 * to call this API directly from the browser during local development, so
 * the frontend/backend health round-trip (Story 1.1 AC) actually works
 * cross-origin (frontend on :5173, backend on :8080).
 *
 * <p>Dev-only allowlist of Vite's default ports, read from the single
 * {@code app.dev-cors-origins} property in {@code application.yml} - the
 * same property also feeds {@code management.endpoints.web.cors}. One
 * property, not two hardcoded lists that can drift apart.
 *
 * <p>Exposed as a {@link CorsConfigurationSource} bean (Story 1.4) - Spring
 * Security's filter chain sits in front of every request, including
 * permitted ones (e.g. {@code /actuator/health}), so {@code SecurityConfig}
 * consumes this bean directly via {@code .cors(...)} and it is now the
 * mechanism actually applied there too - the "dispatched separately" split
 * that justified a second, Actuator-only CORS property (see
 * {@code application.yml}) predates this story and no longer reflects how
 * requests actually flow. This bean replaces the previous
 * {@code WebMvcConfigurer} mapping outright (not side by side): with the
 * Security filter chain now in front of every request, that mechanism would
 * silently stop applying to anything the chain intercepts, leaving two CORS
 * configs that could drift apart - one mechanism, not two.
 *
 * <p>Revisit once the frontend is served from Docker Compose (Epic 4) or a
 * real deployed origin.
 */
@Configuration
public class CorsConfig {

    @Value("${app.dev-cors-origins}")
    private String[] devOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(devOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Unlike the WebMvcConfigurer's CorsRegistry (which defaulted this to
        // "*" implicitly), CorsConfiguration has no implicit default here -
        // it must be set explicitly, or the Authorization header a bearer
        // request relies on would be stripped from preflight, breaking every
        // authenticated cross-origin call.
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
