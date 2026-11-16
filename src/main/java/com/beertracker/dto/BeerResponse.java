package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public catalog representation of a {@code Beer}, excluding internal persistence flags
 * ({@code REQ-F-001}, {@code SRS-ADD-001}).
 *
 * @param id           beer UUID
 * @param name         product name
 * @param brewery      brewery name
 * @param style        beer style
 * @param abv          alcohol by volume percentage
 * @param beerType     product category ({@code PACKAGED} or {@code DRAFT})
 * @param description  optional description
 * @param country      optional ISO 3166-1 alpha-2 country code
 * @param imageUrl     optional label image URI
 * @param status       verification status ({@code UNVERIFIED}, {@code VERIFIED}, {@code REJECTED})
 * @param ratingAvg    aggregated average active review rating (nullable)
 * @param reviewsCount count of active reviews
 * @param packagings   available packaging variants
 * @param createdAt    UTC registration timestamp
 */
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
