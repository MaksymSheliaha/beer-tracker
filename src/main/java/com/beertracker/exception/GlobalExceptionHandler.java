package com.beertracker.exception;

import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.ErrorResponse;
import com.beertracker.filter.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Global exception translator converting all domain and Spring MVC exceptions into the
 * deterministic {@link ErrorResponse} JSON contract enriched with {@code traceId}
 * ({@code REQ-NF-008}, {@code REQ-NF-010}, {@code SRS-ADD-001}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Translates domain {@link ApiException} instances into HTTP error responses.
     *
     * @param ex      thrown domain exception
     * @param request current HTTP request
     * @return standardized {@link ErrorResponse} with the exception's HTTP status
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return buildResponse(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    /**
     * Translates Bean Validation argument errors into {@code 422 Unprocessable Entity}.
     *
     * @param ex      Spring validation failure exception
     * @param request current HTTP request
     * @return {@code 422} {@link ErrorResponse} listing invalid fields
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new ErrorDetail(err.getField(), err.getDefaultMessage()))
                .toList();
        return buildResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNPROCESSABLE_ENTITY",
                "Validation failed for one or more fields",
                details
        );
    }

    /**
     * Translates Jakarta constraint violations into {@code 422 Unprocessable Entity}.
     *
     * @param ex      constraint violation exception
     * @param request current HTTP request
     * @return {@code 422} {@link ErrorResponse} listing violated properties
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> new ErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return buildResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNPROCESSABLE_ENTITY",
                "Validation failed for one or more fields",
                details
        );
    }

    /**
     * Translates malformed or unreadable JSON request bodies into {@code 400 Bad Request}.
     *
     * @param ex      JSON deserialization error
     * @param request current HTTP request
     * @return {@code 400} {@link ErrorResponse} with code {@code MALFORMED_JSON}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "Failed to parse request JSON payload",
                List.of(new ErrorDetail("body", "Malformed JSON request body"))
        );
    }

    /**
     * Translates missing or type-mismatched query/path parameters into {@code 400 Bad Request}.
     *
     * @param ex      parameter binding exception
     * @param request current HTTP request
     * @return {@code 400} {@link ErrorResponse} with code {@code BAD_REQUEST}
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequestParameter(
            Exception ex,
            HttpServletRequest request
    ) {
        String field = "parameter";
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            field = mismatch.getName();
        } else if (ex instanceof MissingServletRequestParameterException missing) {
            field = missing.getParameterName();
        }
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                ex.getMessage(),
                List.of(new ErrorDetail(field, "Invalid or missing request parameter"))
        );
    }

    /**
     * Translates unsupported HTTP verb invocations into {@code 405 Method Not Allowed}.
     *
     * @param ex      Spring method-not-supported exception
     * @param request current HTTP request
     * @return {@code 405} {@link ErrorResponse} with code {@code METHOD_NOT_ALLOWED}
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        String issue = "HTTP method " + ex.getMethod() + " is not supported for this endpoint";
        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                ex.getMessage(),
                List.of(new ErrorDetail("method", issue))
        );
    }

    /**
     * Translates unmapped static/controller paths into {@code 404 Not Found}.
     *
     * @param ex      Spring no-resource-found exception
     * @param request current HTTP request
     * @return {@code 404} {@link ErrorResponse} with code {@code NOT_FOUND}
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "Resource requested at " + request.getRequestURI() + " was not found",
                List.of(new ErrorDetail("path", "Requested resource was not found"))
        );
    }

    /**
     * Fallback handler catching unexpected server faults as {@code 500 Internal Server Error}.
     *
     * @param ex      unhandled exception
     * @param request current HTTP request
     * @return {@code 500} {@link ErrorResponse} with code {@code INTERNAL_SERVER_ERROR}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        String issue = ex.getMessage() != null ? ex.getMessage() : "Unexpected error";
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred",
                List.of(new ErrorDetail("server", issue))
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            List<ErrorDetail> details
    ) {
        String traceId = resolveTraceId();
        MDC.put(TraceIdFilter.MDC_STATUS_KEY, String.valueOf(status.value()));
        LOG.warn("Error response generated [code={}, status={}]: {}", code, status.value(), message);

        ErrorResponse errorResponse = new ErrorResponse(
                code,
                message,
                details != null ? details : List.of(),
                Instant.now(),
                traceId
        );
        return ResponseEntity.status(status)
                .header(TraceIdFilter.TRACE_ID_HEADER, traceId)
                .body(errorResponse);
    }

    private String resolveTraceId() {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            MDC.put(TraceIdFilter.MDC_TRACE_ID_KEY, traceId);
        }
        return traceId;
    }
}
