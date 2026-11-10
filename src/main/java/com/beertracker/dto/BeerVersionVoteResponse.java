package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record BeerVersionVoteResponse(
        @JsonProperty("version_id") UUID versionId,
        @JsonProperty("confirm_votes") int confirmVotes,
        @JsonProperty("reject_votes") int rejectVotes,
        @JsonProperty("status") String status,
        @JsonProperty("message") String message
) {
}
