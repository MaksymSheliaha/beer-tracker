package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Registration request payload creating a new {@code USER} account ({@code REQ-NF-004},
 * {@code REQ-NF-006}).
 *
 * @param email    valid unique email address
 * @param password password (8..128 characters)
 * @param timezone valid IANA timezone identifier (e.g., {@code Europe/Kyiv})
 */
public record UserRegisterRequest(
        @JsonProperty("email") String email,
        @JsonProperty("password") String password,
        @JsonProperty("timezone") String timezone
) {
}
