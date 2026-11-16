package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Paginated catalog response container returning matching {@link BeerResponse} items.
 *
 * @param items         page items
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total matching items across all pages
 * @param totalPages    total available pages
 * @param hasNext       whether a following page exists
 * @param hasPrevious   whether a preceding page exists
 */
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
