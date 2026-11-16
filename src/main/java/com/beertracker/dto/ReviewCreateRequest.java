package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for creating a verified tasting {@code Review} ({@code REQ-F-008}).
 *
 * @param rating     score between {@code 1.0} and {@code 5.0} in {@code 0.5} increments
 * @param reviewText optional tasting notes (max 2000 characters)
 */
public record ReviewCreateRequest(
        @JsonProperty("rating") Double rating,
        @JsonProperty("review_text") String reviewText
) {
}
