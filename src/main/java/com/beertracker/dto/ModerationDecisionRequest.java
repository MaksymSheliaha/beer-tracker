package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Moderator decision payload for resolving a rejected beer in the moderation queue
 * ({@code REQ-F-014}).
 *
 * @param decision final action ({@code APPROVE} or {@code PERMANENT_DELETE})
 * @param comment  optional moderator audit note (not a user {@code Review})
 */
public record ModerationDecisionRequest(
        @JsonProperty("decision") String decision,
        @JsonProperty("comment") String comment
) {
}
