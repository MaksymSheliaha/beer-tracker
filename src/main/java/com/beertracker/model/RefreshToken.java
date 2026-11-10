package com.beertracker.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
        String token,
        UUID userId,
        Instant expiresAt,
        boolean revoked
) {
}
