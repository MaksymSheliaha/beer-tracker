package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLoginRequest(
        @JsonProperty("email") String email,
        @JsonProperty("password") String password
) {
}
