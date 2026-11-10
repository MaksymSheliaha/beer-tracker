package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BeerResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("name") String name,
        @JsonProperty("brewery") String brewery,
        @JsonProperty("style") String style,
        @JsonProperty("abv") Double abv,
        @JsonProperty("beer_type") String beerType,
        @JsonProperty("description") String description,
        @JsonProperty("country") String country,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("status") String status,
        @JsonProperty("rating_avg") Double ratingAvg,
        @JsonProperty("reviews_count") Integer reviewsCount,
        @JsonProperty("packagings") List<BeerPackagingResponse> packagings,
        @JsonProperty("created_at") Instant createdAt
) {
}
