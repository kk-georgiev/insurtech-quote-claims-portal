package com.motorinsurance.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motorinsurance.auth.domain.Role;
import com.motorinsurance.auth.domain.User;
import com.motorinsurance.auth.persistence.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Branch-level coverage for {@link RegistrationService#register} (Story 1.2,
 * Epic 1 retro action item 1). The collaborators are mocked rather than
 * wired to a real Postgres: what this class pins down is the service's own
 * control flow - email normalization applied before both the lookup and the
 * persisted value, the fast-path duplicate rejection, and the
 * {@link DataIntegrityViolationException} → {@link EmailAlreadyRegisteredException}
 * translation for the check-then-act race - none of which need a database to
 * exercise. The end-to-end HTTP contract (real 409 body, real persistence)
 * is covered by {@code auth.api.AuthControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    void register_newEmail_persistsClientUserWithHashedPassword() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plaintext-password")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = registrationService.register("user@example.com", "plaintext-password");

        assertThat(created.getEmail()).isEqualTo("user@example.com");
        assertThat(created.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(created.getRole()).isEqualTo(Role.CLIENT);
    }

    @Test
    void register_normalizesEmailBeforeLookupAndPersistence() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        registrationService.register("  User@Example.COM  ", "plaintext-password");

        // Lookup uses the canonical form...
        verify(userRepository).findByEmail("user@example.com");
        // ...and so does the row actually written.
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void register_emailAlreadyPresent_throwsAndSkipsHashAndInsert() {
        User existing = new User("user@example.com", "existing-hash", Role.CLIENT);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> registrationService.register("user@example.com", "plaintext-password"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        // Fast path: no hashing or insert once the email is known to be taken.
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_concurrentInsertRace_translatesConstraintViolationToConflict() {
        // Both requests for the same email pass the findByEmail check; the DB
        // UNIQUE constraint is the real arbiter and its violation must still
        // surface as the clean 409, not a raw 500 from the @Transactional commit.
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> registrationService.register("user@example.com", "plaintext-password"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void register_conflictException_carriesEmailFieldError() {
        // Epic 1 retro action item 2, asserted at the unit level.
        User existing = new User("user@example.com", "existing-hash", Role.CLIENT);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> registrationService.register("user@example.com", "plaintext-password"))
                .isInstanceOfSatisfying(EmailAlreadyRegisteredException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("AUTH_EMAIL_TAKEN");
                    assertThat(ex.getFieldErrors()).singleElement().satisfies(fe -> assertThat(fe.field())
                            .isEqualTo("email"));
                });
    }
}
