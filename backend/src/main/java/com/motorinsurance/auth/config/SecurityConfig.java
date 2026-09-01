package com.motorinsurance.auth.config;

import com.motorinsurance.auth.application.JwtService;
import com.motorinsurance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
// Jackson 3 (Spring Boot 4 / Spring Framework 7 default), not the classic
// com.fasterxml.jackson.databind.ObjectMapper - see JacksonAutoConfiguration
// in spring-boot-jackson, which registers a tools.jackson.databind.json.JsonMapper
// (an ObjectMapper subtype) as the app's message-converter ObjectMapper bean.
import tools.jackson.databind.ObjectMapper;

/**
 * The gate every request goes through (Story 1.4, AD-3/AD-4/AD-7).
 *
 * <ul>
 *   <li>Stateless, CSRF disabled - bearer-only, no cookies (AD-3).
 *   <li>{@code /actuator/health} and {@code /api/v1/auth/**} stay public -
 *       zero regression on Stories 1.1-1.3; everything else requires
 *       authentication.
 *   <li>The shared {@link JwtAuthenticationFilter} runs ahead of Spring
 *       Security's own authentication filter and populates the security
 *       context; this class never inspects a token itself.
 *   <li>{@code @EnableMethodSecurity} turns on {@code @PreAuthorize}, so a
 *       role-restricted endpoint (Story 1.5's Quote controller, first) can
 *       declare its own required role without this class knowing that
 *       module's URL shape (Design Notes: "Why @PreAuthorize over
 *       path-role mapping").
 *   <li>The {@link CorsConfigurationSource} from {@code CorsConfig} is wired
 *       in directly - Security's filter chain sits in front of every
 *       request, including permitted ones, so CORS must be handled inside
 *       this chain, not as a separate, now-bypassable mechanism.
 *   <li>401 (missing/invalid/expired token) and 403 (valid token, wrong
 *       role) are both written as the exact AD-7 {@link ApiError} envelope
 *       by a custom {@link AuthenticationEntryPoint}/{@link
 *       AccessDeniedHandler} (Design Notes: "Why the entry point/handler
 *       write JSON directly"). The 401 case is rejected inside the filter
 *       chain before {@code DispatcherServlet} ever routes to a controller,
 *       so {@code GlobalExceptionHandler} never sees it. A 403 from
 *       {@code @PreAuthorize}, however, is thrown <em>during</em> controller
 *       method invocation - inside {@code DispatcherServlet} - where {@code
 *       GlobalExceptionHandler}'s own generic exception handling would
 *       otherwise catch it first and turn it into an opaque 500; that class
 *       has a dedicated {@code AccessDeniedException} handler that
 *       re-throws so it reaches this {@link AccessDeniedHandler} instead
 *       (see its javadoc).
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String AUTH_UNAUTHENTICATED_CODE = "AUTH_UNAUTHENTICATED";
    private static final String AUTH_FORBIDDEN_CODE = "AUTH_FORBIDDEN";

    // Bare-path matchers deliberately NOT used here: they'd permit *any*
    // HTTP method on these paths, so a future handler added at the same
    // path (any verb) would silently inherit public access. Scoping to the
    // actual method each endpoint uses keeps "public" as narrow as it is
    // today.
    private static final String[] PUBLIC_POST_ENDPOINTS = {"/api/v1/auth/register", "/api/v1/auth/login"};

    // springdoc's own routes (Story 9.1, FR-M3-14): the generated OpenAPI
    // document and the Swagger UI that renders it. A docs endpoint for
    // reviewers/teammates, not user data - same public-GET treatment as
    // /actuator/health above. Production hardening is explicitly out of
    // scope for this milestone (see deferred-work.md).
    private static final String[] PUBLIC_GET_ENDPOINTS = {
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/actuator/health")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Renders every 401 (missing/invalid/expired token) as the AD-7 {@code ApiError} envelope. */
    private AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> writeError(
                response,
                objectMapper,
                HttpStatus.UNAUTHORIZED,
                AUTH_UNAUTHENTICATED_CODE,
                "Authentication is required to access this resource");
    }

    /** Renders every 403 (valid token, wrong role) as the AD-7 {@code ApiError} envelope. */
    private AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeError(
                response,
                objectMapper,
                HttpStatus.FORBIDDEN,
                AUTH_FORBIDDEN_CODE,
                "You do not have permission to access this resource");
    }

    private static void writeError(
            HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String code, String message)
            throws IOException {
        ApiError body = ApiError.of(status.value(), code, message);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
