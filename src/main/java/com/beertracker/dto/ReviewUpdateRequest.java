package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for idempotently updating an existing active {@code Review} ({@code REQ-F-008}).
 *
 * @param rating     updated score between {@code 1.0} and {@code 5.0} in {@code 0.5} increments
 * @param reviewText optional updated tasting notes (max 2000 characters)
 */
public record ReviewUpdateRequest(
        @JsonProperty("rating") Double rating,
        @JsonProperty("review_text") String reviewText
) {
}
