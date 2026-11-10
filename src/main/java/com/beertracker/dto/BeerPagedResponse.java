package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BeerPagedResponse(
        @JsonProperty("items") List<BeerResponse> items,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elements") int totalElements,
        @JsonProperty("total_pages") int totalPages,
        @JsonProperty("has_next") boolean hasNext,
        @JsonProperty("has_previous") boolean hasPrevious
) {
}
