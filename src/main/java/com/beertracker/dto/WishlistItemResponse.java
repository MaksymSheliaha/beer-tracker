package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a {@code Wishlist} entry embedding its catalog {@link BeerResponse}
 * ({@code REQ-F-009}).
 *
 * @param id      wishlist entry UUID
 * @param beerId  saved catalog beer UUID
 * @param addedAt UTC timestamp when added to wishlist
 * @param beer    embedded catalog beer details (may be {@code null} if unavailable)
 */
public record WishlistItemResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("added_at") Instant addedAt,
        @JsonProperty("beer") BeerResponse beer
) {
}
