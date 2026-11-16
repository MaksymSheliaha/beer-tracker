package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Authentication response containing JWT Access Token, Refresh Token, and TTL metadata
 * ({@code REQ-NF-004}).
 *
 * @param accessToken  HS256-signed Bearer JWT Access Token (15-minute TTL)
 * @param refreshToken opaque single-use Refresh Token (30-day TTL)
 * @param tokenType    authentication scheme type ({@code Bearer})
 * @param expiresIn    Access Token lifetime in seconds ({@code 900})
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") int expiresIn
) {
}
