package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record BeerMergeRequest(
        @JsonProperty("source_beer_id") UUID sourceBeerId,
        @JsonProperty("target_beer_id") UUID targetBeerId
) {
}
