package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record WishlistItemResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("added_at") Instant addedAt,
        @JsonProperty("beer") BeerResponse beer
) {
}
