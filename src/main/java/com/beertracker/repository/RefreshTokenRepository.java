package com.beertracker.repository;

import com.beertracker.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RefreshToken} entities supporting single-use rotation
 * ({@code REQ-NF-004}).
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * Atomically verifies that a refresh token exists, is not revoked, and has not expired,
     * then marks it revoked so it cannot be reused.
     *
     * @param tokenValue opaque refresh token string
     * @param now        current server timestamp
     * @return consumed {@link RefreshToken} if valid and active, or empty otherwise
     */
    default Optional<RefreshToken> consumeIfActive(String tokenValue, Instant now) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> existingOpt = findById(tokenValue.trim());
        if (existingOpt.isPresent()) {
            RefreshToken existing = existingOpt.get();
            if (!existing.revoked() && existing.expiresAt().isAfter(now)) {
                existing.setRevoked(true);
                saveAndFlush(existing);
                return Optional.of(existing);
            }
        }
        return Optional.empty();
    }
}
