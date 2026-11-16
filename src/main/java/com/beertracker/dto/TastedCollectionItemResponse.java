package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregated entry in a user's virtual {@code Tasted Collection} ({@code REQ-F-009}).
 *
 * @param beerId          tasted beer UUID
 * @param beerName        current display name (or placeholder label if soft-deleted)
 * @param brewery         brewery name
 * @param totalVolumeMl   cumulative consumed volume in milliliters for this beer
 * @param firstConsumedAt UTC timestamp of the first recorded consumption
 * @param lastConsumedAt  UTC timestamp of the latest recorded consumption
 * @param userRating      user's active review rating if present, otherwise {@code null}
 */
public record TastedCollectionItemResponse(
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("beer_name") String beerName,
        @JsonProperty("brewery") String brewery,
        @JsonProperty("total_volume_ml") int totalVolumeMl,
        @JsonProperty("first_consumed_at") Instant firstConsumedAt,
        @JsonProperty("last_consumed_at") Instant lastConsumedAt,
        @JsonProperty("user_rating") Double userRating
) {
}
