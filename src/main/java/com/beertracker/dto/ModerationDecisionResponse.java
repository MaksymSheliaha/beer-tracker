package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ModerationDecisionResponse(
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("decision") String decision,
        @JsonProperty("processed_at") Instant processedAt
) {
}
