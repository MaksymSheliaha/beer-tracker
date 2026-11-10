package com.beertracker.model;

import java.time.Instant;
import java.util.UUID;

public record WishlistItem(
        UUID id,
        UUID userId,
        UUID beerId,
        Instant addedAt
) {
}
