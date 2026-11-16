package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Sanitized public user profile returned upon registration, never exposing
 * {@code password_hash} ({@code REQ-NF-006}).
 *
 * @param id        user UUID
 * @param email     normalized lowercase email address
 * @param role      assigned RBAC role ({@code USER} or {@code MODERATOR})
 * @param timezone  configured IANA timezone identifier
 * @param createdAt UTC registration timestamp
 */
public record UserResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("email") String email,
        @JsonProperty("role") String role,
        @JsonProperty("timezone") String timezone,
        @JsonProperty("created_at") Instant createdAt
) {
}
