package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Vote tally and resulting lifecycle status returned after casting a community vote
 * ({@code REQ-F-004}, {@code REQ-F-005}).
 *
 * @param versionId    voted version UUID
 * @param confirmVotes updated {@code CONFIRM} vote count
 * @param rejectVotes  updated {@code REJECT} vote count
 * @param status       resulting version status ({@code UNVERIFIED}, {@code VERIFIED}, {@code REJECTED})
 * @param message      human-readable summary of the vote outcome
 */
public record BeerVersionVoteResponse(
        @JsonProperty("version_id") UUID versionId,
        @JsonProperty("confirm_votes") int confirmVotes,
        @JsonProperty("reject_votes") int rejectVotes,
        @JsonProperty("status") String status,
        @JsonProperty("message") String message
) {
}
