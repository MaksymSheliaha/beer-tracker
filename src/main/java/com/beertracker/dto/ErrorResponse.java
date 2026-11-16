package com.beertracker.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standardized error payload returned for all {@code 4xx} and {@code 5xx} API responses
 * ({@code REQ-NF-008}, {@code REQ-NF-010}, {@code SRS-ADD-001}).
 *
 * @param code      machine-readable {@code SCREAMING_SNAKE_CASE} domain error code
 * @param message   human-readable error summary
 * @param details   non-null list of field-level {@link ErrorDetail} items
 * @param timestamp UTC timestamp when the error response was produced
 * @param traceId   request correlation UUID matching {@code X-Trace-Id} and MDC {@code trace_id}
 */
public record ErrorResponse(
        String code,
        String message,
        List<ErrorDetail> details,
        Instant timestamp,
        String traceId
) {
}
