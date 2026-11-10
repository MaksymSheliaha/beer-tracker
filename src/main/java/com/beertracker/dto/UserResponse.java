package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("email") String email,
        @JsonProperty("role") String role,
        @JsonProperty("timezone") String timezone,
        @JsonProperty("created_at") Instant createdAt
) {
}
