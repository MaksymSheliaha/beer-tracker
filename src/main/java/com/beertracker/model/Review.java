package com.beertracker.model;

import java.time.Instant;
import java.util.UUID;

public record Review(
        UUID id,
        UUID beerId,
        UUID userId,
        Double rating,
        String reviewText,
        boolean isArchived,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
