package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BeerVersionCreateRequest(
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
