package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ConsumptionEntryResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("packaging_id") UUID packagingId,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("total_volume_ml") Integer totalVolumeMl,
        @JsonProperty("consumed_at") Instant consumedAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("wishlist_autoremoved") boolean wishlistAutoremoved,
        @JsonProperty("collection_added") boolean collectionAdded,
        @JsonProperty("beer_name") String beerName,
        @JsonProperty("packaging_volume_ml") Integer packagingVolumeMl
) {
}
