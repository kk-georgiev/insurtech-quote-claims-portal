package com.motorinsurance.auth.application;

import com.motorinsurance.auth.domain.Emails;
import com.motorinsurance.auth.domain.Role;
import com.motorinsurance.auth.domain.User;
import com.motorinsurance.auth.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-registration use case (Story 1.2). Always creates a {@link Role#CLIENT}
 * user - {@code RegisterRequest} carries no {@code role} field, so
 * self-registration cannot produce a staff account by construction (see
 * Story 1.2 Boundaries & Constraints).
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword) {
        // Normalize before both the lookup and the persisted value: emails
        // are case-insensitive in practice (mobile keyboards auto-capitalize,
        // people type carelessly), but `users.email` is a plain case-sensitive
        // UNIQUE column. AuthenticationService's login lookup must apply the
        // identical transformation - hence the shared Emails.normalize helper.
        // A separate final variable (not a reassigned parameter) because the
        // lambda below captures it - reassigning `email` itself would no
        // longer be effectively final and fail to compile.
        final String normalizedEmail = Emails.normalize(email);

        // Fast path for the common case - avoids a hash+insert round trip
        // when the email is obviously already taken.
        userRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        });

        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = new User(normalizedEmail, passwordHash, Role.CLIENT);
        try {
            // saveAndFlush (not save) is required here: plain save() only
            // schedules the INSERT and normally defers it to transaction
            // commit, which happens *after* this method returns - a
            // constraint violation would then escape this try/catch entirely
            // and surface as an opaque 500 from the @Transactional commit,
            // never reaching the handler below. Flushing forces the INSERT
            // (and any UNIQUE-constraint violation) to happen synchronously,
            // right here.
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Backstop for the check-then-act race: two concurrent requests
            // for the same email can both pass the findByEmail check above:
            // the DB's UNIQUE constraint on users.email is the actual source
            // of truth, and its violation here still maps to the same clean
            // 409 AUTH_EMAIL_TAKEN instead of falling through as a raw 500.
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }
    }
}
