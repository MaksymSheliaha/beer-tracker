package com.beertracker.repository;

import com.beertracker.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

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
