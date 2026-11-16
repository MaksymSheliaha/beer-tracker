package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Request payload for recording an immutable {@code ConsumptionEntry} ({@code REQ-F-006}).
 *
 * @param beerId      consumed catalog beer UUID
 * @param packagingId selected packaging UUID belonging to {@code beerId}
 * @param quantity    number of consumed packaging units ({@code 1..100})
 * @param consumedAt  UTC consumption timestamp (not in future, within past 365 days,
 *                    and not prior to user registration)
 */
public record ConsumptionEntryCreateRequest(
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("packaging_id") UUID packagingId,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("consumed_at") Instant consumedAt
) {
}
