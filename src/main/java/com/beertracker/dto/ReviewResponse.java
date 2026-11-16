package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of an active user tasting {@code Review}, omitting internal archival
 * flags ({@code REQ-F-008}, {@code SRS-ADD-001}).
 *
 * @param id         review UUID
 * @param beerId     reviewed beer UUID
 * @param userId     author user UUID
 * @param rating     score between {@code 1.0} and {@code 5.0}
 * @param reviewText optional tasting notes
 * @param createdAt  UTC creation timestamp
 * @param updatedAt  UTC last-update timestamp
 */
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
