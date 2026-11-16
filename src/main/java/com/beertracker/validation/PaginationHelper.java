package com.beertracker.validation;

import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Shared validation and deterministic in-memory slicing helpers for paginated REST endpoints.
 *
 * <p>Enforces the standardized OpenAPI pagination bounds ({@code page >= 0},
 * {@code 1 <= size <= 100}) across all collection resources.</p>
 */
public final class PaginationHelper {

    private PaginationHelper() {
    }

    /**
     * Validates zero-based {@code page} and bounded {@code size} query parameters.
     *
     * @param page zero-based page index (must be {@code >= 0})
     * @param size page size (must be between {@code 1} and {@code 100} inclusive)
     * @throws ApiException with HTTP {@code 400 BAD_REQUEST} or {@code INVALID_PAGE_SIZE}
     *                      when pagination bounds are violated
     */
    public static void validate(int page, int size) {
        if (!DomainValidationPredicates.IS_VALID_PAGE_INDEX.test(page)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Parameter 'page' must not be negative",
                    List.of(new ErrorDetail("page", "Must be greater than or equal to 0"))
            );
        }
        if (!DomainValidationPredicates.IS_VALID_PAGE_SIZE.test(size)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_SIZE",
                    "Parameter 'size' exceeds maximum allowed limit of 100",
                    List.of(new ErrorDetail("size", "Must be between 1 and 100"))
            );
        }
    }

    /**
     * Slices a complete list of items into a single requested page with metadata.
     *
     * @param allItems full ordered list of items
     * @param page     zero-based page index
     * @param size     maximum items per page
     * @param <T>      element type
     * @return immutable {@link PageSlice} containing the page subset and pagination metadata
     */
    public static <T> PageSlice<T> slice(List<T> allItems, int page, int size) {
        int totalElements = allItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<T> pageItems = List.copyOf(allItems.subList(fromIndex, toIndex));
        return new PageSlice<>(
                pageItems,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages,
                page > 0
        );
    }

    /**
     * Immutable slice result holding paginated items and navigation metadata.
     *
     * @param items         elements on the requested page
     * @param page          zero-based page index
     * @param size          requested page size
     * @param totalElements total matching elements across all pages
     * @param totalPages    total number of available pages
     * @param hasNext       whether a subsequent page exists
     * @param hasPrevious   whether a preceding page exists
     * @param <T>           element type
     */
    public record PageSlice<T>(
            List<T> items,
            int page,
            int size,
            int totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }
}
