package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Paginated container returning {@link ConsumptionEntryResponse} records for the authenticated owner.
 *
 * @param items         page items
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total user consumption entries
 * @param totalPages    total available pages
 * @param hasNext       whether a following page exists
 * @param hasPrevious   whether a preceding page exists
 */
public record ConsumptionPagedResponse(
        @JsonProperty("items") List<ConsumptionEntryResponse> items,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elements") int totalElements,
        @JsonProperty("total_pages") int totalPages,
        @JsonProperty("has_next") boolean hasNext,
        @JsonProperty("has_previous") boolean hasPrevious
) {
}
