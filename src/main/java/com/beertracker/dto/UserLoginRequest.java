package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Login credentials payload for obtaining JWT Access and Refresh tokens ({@code REQ-NF-004}).
 *
 * @param email    registered user email address
 * @param password raw user password verified against stored BCrypt hash
 */
public record UserLoginRequest(
        @JsonProperty("email") String email,
        @JsonProperty("password") String password
) {
}
