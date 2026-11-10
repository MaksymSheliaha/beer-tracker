package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WishlistPagedResponse(
        @JsonProperty("items") List<WishlistItemResponse> items,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages
) {
}
