package com.beertracker.repository;

import com.beertracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User} accounts ({@code REQ-NF-004}, {@code REQ-NF-006}).
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Looks up a user by exact email ignoring ASCII/Unicode case.
     *
     * @param email normalized email string
     * @return matching user account if registered
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Trims and resolves a user by email case-insensitively.
     *
     * @param email raw email input
     * @return matching user if non-blank and found
     */
    default Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return findByEmailIgnoreCase(email.trim());
    }
}
