package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record BeerVersionResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("author_id") UUID authorId,
        @JsonProperty("payload") BeerVersionCreateRequest payload,
        @JsonProperty("confirm_votes") int confirmVotes,
        @JsonProperty("reject_votes") int rejectVotes,
        @JsonProperty("status") String status,
        @JsonProperty("created_at") Instant createdAt
) {
}
