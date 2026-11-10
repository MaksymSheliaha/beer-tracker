package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("rating") Double rating,
        @JsonProperty("review_text") String reviewText,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
}
