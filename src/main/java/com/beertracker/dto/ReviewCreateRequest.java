package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewCreateRequest(
        @JsonProperty("rating") Double rating,
        @JsonProperty("review_text") String reviewText
) {
}
