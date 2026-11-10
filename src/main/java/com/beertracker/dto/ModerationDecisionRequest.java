package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModerationDecisionRequest(
        @JsonProperty("decision") String decision,
        @JsonProperty("comment") String comment
) {
}
