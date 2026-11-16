package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Quantitative summary returned after executing an atomic catalog duplicate merge ({@code REQ-F-011}).
 *
 * @param targetBeerId              UUID of the canonical surviving beer
 * @param migratedConsumptionsCount number of consumption entries re-linked to the target beer
 * @param migratedPackagingsCount   number of missing packagings created on the target beer
 * @param archivedReviewsCount      number of older duplicate reviews archived
 * @param status                    resulting merge status ({@code MERGED})
 */
public record BeerMergeResponse(
        @JsonProperty("target_beer_id") UUID targetBeerId,
        @JsonProperty("migrated_consumptions_count") int migratedConsumptionsCount,
        @JsonProperty("migrated_packagings_count") int migratedPackagingsCount,
        @JsonProperty("archived_reviews_count") int archivedReviewsCount,
        @JsonProperty("status") String status
) {
}
