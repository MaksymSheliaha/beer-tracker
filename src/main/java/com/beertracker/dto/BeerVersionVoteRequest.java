package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BeerVersionVoteRequest(
        @JsonProperty("vote_type") String voteType
) {
}
