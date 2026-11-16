package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload submitting a Refresh Token for single-use rotation ({@code REQ-NF-004}).
 *
 * @param refreshToken previously issued active Refresh Token string
 */
public record RefreshTokenRequest(
        @JsonProperty("refresh_token") String refreshToken
) {
}
