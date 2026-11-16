package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Administrative request payload to merge a duplicate catalog beer into a canonical target
 * ({@code REQ-F-011}).
 *
 * @param sourceBeerId UUID of the duplicate beer to mark as {@code MERGED}
 * @param targetBeerId UUID of the surviving canonical beer
 */
public record BeerMergeRequest(
        @JsonProperty("source_beer_id") UUID sourceBeerId,
        @JsonProperty("target_beer_id") UUID targetBeerId
) {
}
