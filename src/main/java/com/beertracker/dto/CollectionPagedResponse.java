package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Paginated container returning a user's virtual {@code Tasted Collection} entries ({@code REQ-F-009}).
 *
 * @param items         tasted beer summary items on the requested page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total unique tasted beers
 * @param totalPages    total available pages
 */
public record CollectionPagedResponse(
        @JsonProperty("items") List<TastedCollectionItemResponse> items,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages
) {
}
