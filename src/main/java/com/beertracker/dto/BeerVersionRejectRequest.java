package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Optional moderator request payload specifying a reason when rejecting a {@code BeerVersion}
 * draft ({@code REQ-F-013}).
 *
 * @param reason optional human-readable rejection reason (max 1000 characters)
 */
public record BeerVersionRejectRequest(
        @JsonProperty("reason") String reason
) {
}
