package com.beertracker.dto;

/**
 * Field-level error detail item inside {@link ErrorResponse} ({@code REQ-NF-008}).
 *
 * @param field parameter, header, or request body field name that caused the error
 * @param issue specific human-readable description of the violation
 */
public record ErrorDetail(
        String field,
        String issue
) {
}
