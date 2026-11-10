package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record BeerMergeResponse(
        @JsonProperty("target_beer_id") UUID targetBeerId,
        @JsonProperty("migrated_consumptions_count") int migratedConsumptionsCount,
        @JsonProperty("migrated_packagings_count") int migratedPackagingsCount,
        @JsonProperty("archived_reviews_count") int archivedReviewsCount,
        @JsonProperty("status") String status
) {
}
