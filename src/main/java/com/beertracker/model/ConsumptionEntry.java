package com.beertracker.model;

import java.time.Instant;
import java.util.UUID;

public record ConsumptionEntry(
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
}
