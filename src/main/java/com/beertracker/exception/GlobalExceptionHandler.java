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

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return buildResponse(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails());
    }

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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                ex.getMessage(),
                List.of(new ErrorDetail("method", "HTTP method " + ex.getMethod() + " is not supported for this endpoint"))
        );
    }

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred",
                List.of(new ErrorDetail("server", ex.getMessage() != null ? ex.getMessage() : "Unexpected error"))
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
        log.warn("Error response generated [code={}, status={}]: {}", code, status.value(), message);

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
