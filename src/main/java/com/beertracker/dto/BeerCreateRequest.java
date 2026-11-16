package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request payload for registering a new catalog {@code Beer} entry ({@code REQ-F-001}).
 *
 * @param name        product name
 * @param brewery     brewery name
 * @param style       beer style
 * @param abv         alcohol by volume percentage ({@code 0.0..100.0})
 * @param beerType    product category ({@code PACKAGED} or {@code DRAFT})
 * @param description optional product description
 * @param country     optional ISO 3166-1 alpha-2 country code
 * @param imageUrl    optional label image URL
 * @param packagings  non-empty list of initial packaging options
 */
public record BeerCreateRequest(
        @JsonProperty("name") String name,
        @JsonProperty("brewery") String brewery,
        @JsonProperty("style") String style,
        @JsonProperty("abv") Double abv,
        @JsonProperty("beer_type") String beerType,
        @JsonProperty("description") String description,
        @JsonProperty("country") String country,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("packagings") List<BeerPackagingCreateRequest> packagings
) {
}
