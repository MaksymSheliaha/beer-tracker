package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewUpdateRequest(
        @JsonProperty("rating") Double rating,
        @JsonProperty("review_text") String reviewText
) {
}
