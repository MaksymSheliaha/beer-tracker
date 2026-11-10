package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

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
