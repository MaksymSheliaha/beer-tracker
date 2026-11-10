package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ConsumptionEntryCreateRequest(
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("packaging_id") UUID packagingId,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("consumed_at") Instant consumedAt
) {
}
