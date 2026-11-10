package com.beertracker.repository;

import com.beertracker.model.RefreshToken;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RefreshTokenRepository {

    private final Map<String, RefreshToken> tokens = new ConcurrentHashMap<>();

    public void save(RefreshToken refreshToken) {
        tokens.put(refreshToken.token(), refreshToken);
    }

    public Optional<RefreshToken> consumeIfActive(String tokenValue, Instant now) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        final RefreshToken[] consumedHolder = new RefreshToken[1];
        tokens.computeIfPresent(tokenValue.trim(), (key, existing) -> {
            if (!existing.revoked() && existing.expiresAt().isAfter(now)) {
                consumedHolder[0] = existing;
                return new RefreshToken(existing.token(), existing.userId(), existing.expiresAt(), true);
            }
            return existing;
        });
        return Optional.ofNullable(consumedHolder[0]);
    }
}
