package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
