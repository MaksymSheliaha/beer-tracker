package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a {@code BeerVersion} draft and its community vote counts
 * ({@code REQ-F-003}, {@code REQ-F-004}).
 *
 * @param id           version UUID
 * @param beerId       target catalog beer UUID
 * @param authorId     proposing user UUID
 * @param payload      proposed attribute and packaging updates
 * @param confirmVotes accumulated {@code CONFIRM} vote count
 * @param rejectVotes  accumulated {@code REJECT} vote count
 * @param status       lifecycle status ({@code UNVERIFIED}, {@code VERIFIED}, {@code REJECTED})
 * @param createdAt    UTC draft creation timestamp
 */
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
