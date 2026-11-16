package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit response returned after applying a moderation queue decision ({@code REQ-F-014}).
 *
 * @param beerId      processed beer UUID
 * @param decision    executed decision ({@code APPROVE} or {@code PERMANENT_DELETE})
 * @param processedAt UTC timestamp when the decision was applied
 */
public record ModerationDecisionResponse(
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("decision") String decision,
        @JsonProperty("processed_at") Instant processedAt
) {
}
