package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BeerVersionPagedResponse(
        @JsonProperty("items") List<BeerVersionResponse> items,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elements") int totalElements,
        @JsonProperty("total_pages") int totalPages
) {
}
