package com.motorinsurance.auth.application;

import com.motorinsurance.auth.domain.Emails;
import com.motorinsurance.auth.domain.User;
import com.motorinsurance.auth.persistence.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login use case (Story 1.3): looks up the user by email, verifies the
 * password, and issues a JWT on success - or throws
 * {@link InvalidCredentialsException} for either failure, with nothing in
 * the response or its timing distinguishing "wrong password" from "unknown
 * email" (AD-3 no user enumeration; see spec Design Notes: Timing-safe
 * lookup).
 */
@Service
public class AuthenticationService {

    // A syntactically valid BCrypt hash of a fixed, never-issued plaintext
    // that no real password will ever equal. When no user is found,
    // passwordEncoder.matches(...) still runs against this hash instead of
    // being skipped, so the "unknown email" path costs the same BCrypt work
    // as the "wrong password" path - skipping the hash entirely for a
    // missing user would make that response measurably faster and leak
    // account existence via timing alone, even though the response bodies
    // are identical.
    private static final String DUMMY_PASSWORD_HASH =
            "$2b$10$lT1CHQ4x77XARe9UvVN7Ge7RPvuAO4Oh3888BuS2edrMHIsMhMlGC";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Verifies {@code email}/{@code rawPassword} and returns a signed JWT, or
     * throws {@link InvalidCredentialsException} if the email is unknown or
     * the password doesn't match.
     */
    public String login(String email, String rawPassword) {
        // Must match RegistrationService's normalization exactly, or a user
        // who registered as "User@Example.com" (stored lowercased) could
        // never log back in with a differently-cased attempt against this
        // case-sensitive lookup - hence the shared Emails.normalize helper.
        String normalizedEmail = Emails.normalize(email);
        Optional<User> user = userRepository.findByEmail(normalizedEmail);

        // matches(...) always runs, dummy hash or real one - see
        // DUMMY_PASSWORD_HASH javadoc above for why this can't be
        // short-circuited for a missing user.
        String hashToCheck = user.map(User::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCheck);

        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        User found = user.get();
        return jwtService.issueToken(found.getId(), found.getRole());
    }
}
