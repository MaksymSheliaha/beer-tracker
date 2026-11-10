package com.beertracker.model;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String passwordHash,
        String role,
        String timezone,
        Instant createdAt
) {
}
