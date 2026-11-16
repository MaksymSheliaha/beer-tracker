package com.beertracker.exception;

import com.beertracker.dto.ErrorDetail;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Domain-specific runtime exception mapped to a standardized {@code ErrorResponse}
 * HTTP payload by {@link GlobalExceptionHandler} ({@code REQ-NF-008}).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ErrorDetail> details;

    /**
     * Constructs an {@link ApiException} with HTTP status, machine-readable error code,
     * summary message, and field-level violation details.
     *
     * @param status  target HTTP response status ({@code 4xx} or {@code 5xx})
     * @param code    machine-readable {@code SCREAMING_SNAKE_CASE} domain error code
     * @param message human-readable summary description
     * @param details optional list of field-level error explanations (defaults to empty list)
     */
    public ApiException(HttpStatus status, String code, String message, List<ErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    /**
     * Returns the HTTP status associated with this exception.
     *
     * @return HTTP status code
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Returns the standardized machine-readable error code.
     *
     * @return {@code SCREAMING_SNAKE_CASE} error code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns an immutable list of field-level error details.
     *
     * @return immutable list of {@link ErrorDetail} entries
     */
    public List<ErrorDetail> getDetails() {
        return details;
    }
}
