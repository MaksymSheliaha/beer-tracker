package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Community voting request payload for a {@code BeerVersion} draft ({@code REQ-F-004}).
 *
 * @param voteType vote direction ({@code CONFIRM} or {@code REJECT})
 */
public record BeerVersionVoteRequest(
        @JsonProperty("vote_type") String voteType
) {
}
