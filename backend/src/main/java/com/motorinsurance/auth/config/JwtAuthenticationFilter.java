package com.motorinsurance.auth.config;

import com.motorinsurance.auth.application.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The one shared JWT authentication filter (Story 1.4, AD-2/AD-3: "auth's
 * JWT filter"). Every module reads "who is the current user" from the
 * Spring Security context this filter populates - never by calling
 * {@code auth}'s services directly.
 *
 * <p>Reads {@code Authorization: Bearer <jwt>}, validates it via
 * {@link JwtService#parseToken}, and - on success - sets an authenticated
 * {@link UsernamePasswordAuthenticationToken} (principal: the user id;
 * authority: {@code ROLE_<role>}) on the {@link SecurityContextHolder}.
 *
 * <p>On a missing header or an invalid/expired token, this filter does
 * <strong>not</strong> reject the request itself - it leaves the security
 * context empty and continues the chain. {@code SecurityConfig}'s
 * {@code anyRequest().authenticated()} rule (backed by its
 * {@code AuthenticationEntryPoint}) is what turns that empty context into a
 * uniform 401 further down the chain, so every "no valid token" path -
 * missing header, malformed token, bad signature, expired token - produces
 * the exact same {@code AUTH_UNAUTHENTICATED} response, from one place.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                JwtService.ParsedToken parsed = jwtService.parseToken(token);
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + parsed.role().name());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(parsed.userId(), null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Bad signature, malformed token, expired exp, blank token,
                // or a syntactically-valid-but-nonsensical claim (see
                // JwtService#parseToken) - all treated identically: leave
                // the context empty, see class javadoc for why.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
