package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request payload proposing attribute updates and/or additional packagings for an existing
 * catalog {@code Beer} via a {@code BeerVersion} draft ({@code REQ-F-003}).
 *
 * @param name        optional updated beer name
 * @param brewery     optional updated brewery name
 * @param style       optional updated style
 * @param abv         optional updated ABV percentage ({@code 0.0..100.0})
 * @param beerType    optional updated beer category ({@code PACKAGED} or {@code DRAFT})
 * @param description optional updated description
 * @param country     optional updated ISO 3166-1 alpha-2 country code
 * @param imageUrl    optional updated image URL
 * @param packagings  optional list of new packaging variants to append upon verification
 */
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
