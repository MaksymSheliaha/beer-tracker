package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserRegisterRequest(
        @JsonProperty("email") String email,
        @JsonProperty("password") String password,
        @JsonProperty("timezone") String timezone
) {
}
