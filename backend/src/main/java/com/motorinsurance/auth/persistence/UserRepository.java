package com.motorinsurance.auth.persistence;

import com.motorinsurance.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link User}. {@code findByEmail} backs
 * both the Story 1.2 duplicate-email check and Story 1.3's future login
 * lookup.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
}
