package com.beertracker.model;

import java.time.Instant;
import java.util.UUID;

public record ConsumptionEntry(
        UUID id,
        UUID userId,
        UUID beerId,
        UUID packagingId,
        Integer quantity,
        Integer totalVolumeMl,
        Instant consumedAt,
        Instant createdAt,
        boolean wishlistAutoremoved,
        boolean collectionAdded,
        String beerName,
        Integer packagingVolumeMl
) {
    public ConsumptionEntry(
            UUID id,
            UUID beerId,
            UUID packagingId,
            Integer quantity,
            Integer totalVolumeMl,
            Instant consumedAt,
            Instant createdAt,
            boolean wishlistAutoremoved,
            boolean collectionAdded,
            String beerName,
            Integer packagingVolumeMl
    ) {
        this(
                id,
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                beerId,
                packagingId,
                quantity,
                totalVolumeMl,
                consumedAt,
                createdAt,
                wishlistAutoremoved,
                collectionAdded,
                beerName,
                packagingVolumeMl
        );
    }
}
