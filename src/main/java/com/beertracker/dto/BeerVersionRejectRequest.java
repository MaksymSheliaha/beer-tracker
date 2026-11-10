package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BeerVersionRejectRequest(
        @JsonProperty("reason") String reason
) {
}
