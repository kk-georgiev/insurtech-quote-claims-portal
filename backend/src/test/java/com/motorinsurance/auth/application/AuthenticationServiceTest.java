package com.motorinsurance.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.motorinsurance.auth.domain.Role;
import com.motorinsurance.auth.domain.User;
import com.motorinsurance.auth.persistence.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Branch-level coverage for {@link AuthenticationService#login} (Story 1.3,
 * Epic 1 retro action item 1). Mocked collaborators, because what matters
 * here is control flow that a real DB would only obscure: the same
 * {@link InvalidCredentialsException} for an unknown email and for a wrong
 * password, and - the point of {@code DUMMY_PASSWORD_HASH} - that the BCrypt
 * comparison still runs on the unknown-email path so it can't be told apart
 * by timing (AD-3, no user enumeration). The real-token round trip out of a
 * live {@code POST /api/v1/auth/login} is covered by
 * {@code auth.api.AuthControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void login_validCredentials_returnsTokenForThatUserAndRole() {
        User user = new User("user@example.com", "bcrypt-hash", Role.CLIENT);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plaintext-password", "bcrypt-hash")).thenReturn(true);
        when(jwtService.issueToken(user.getId(), Role.CLIENT)).thenReturn("signed-jwt");

        String token = authenticationService.login("user@example.com", "plaintext-password");

        assertThat(token).isEqualTo("signed-jwt");
    }

    @Test
    void login_normalizesEmailBeforeLookup() {
        User user = new User("user@example.com", "bcrypt-hash", Role.CLIENT);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.issueToken(any(), any())).thenReturn("signed-jwt");

        authenticationService.login("  User@Example.COM  ", "plaintext-password");

        verify(userRepository).findByEmail("user@example.com");
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentialsButStillRunsHashComparison() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        // matches(...) is still invoked - against the dummy BCrypt hash - so
        // the unknown-email path costs the same as the wrong-password path.
        when(passwordEncoder.matches(eq("plaintext-password"), argThat(hash -> hash.startsWith("$2"))))
                .thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login("nobody@example.com", "plaintext-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder).matches(eq("plaintext-password"), argThat(hash -> hash.startsWith("$2")));
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsAndIssuesNoToken() {
        User user = new User("user@example.com", "bcrypt-hash", Role.CLIENT);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login("user@example.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> {
                    InvalidCredentialsException ice = (InvalidCredentialsException) ex;
                    // Nothing on the exception distinguishes this from the
                    // unknown-email case above: same 401, same code, no field
                    // error naming which half was wrong (AD-3).
                    assertThat(ice.getStatus()).isEqualTo(401);
                    assertThat(ice.getCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
                    assertThat(ice.getFieldErrors()).isEmpty();
                });

        verify(jwtService, never()).issueToken(any(), any());
    }
}
