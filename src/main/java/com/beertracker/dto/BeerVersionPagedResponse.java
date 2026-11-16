package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Paginated container returning {@link BeerVersionResponse} drafts filtered by status.
 *
 * @param items         page items
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total matching version drafts
 * @param totalPages    total available pages
 */
public record BeerVersionPagedResponse(
        @JsonProperty("items") List<BeerVersionResponse> items,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elements") int totalElements,
        @JsonProperty("total_pages") int totalPages
) {
}
