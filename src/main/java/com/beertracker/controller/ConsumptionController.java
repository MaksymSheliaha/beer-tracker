package com.beertracker.controller;

import com.beertracker.dto.ConsumptionEntryCreateRequest;
import com.beertracker.dto.ConsumptionEntryResponse;
import com.beertracker.dto.ConsumptionPagedResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.security.JwtRequestAuthenticator;
import com.beertracker.service.ConsumptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * REST controller for the append-only {@code ConsumptionEntry} journal under
 * {@code /api/v1/consumption-entries}.
 *
 * <p>Enforces Bearer JWT authentication, strict per-user data isolation returning
 * {@code 404 Not Found} on foreign records ({@code REQ-NF-005}), and blocks mutating HTTP
 * verbs ({@code PUT}, {@code PATCH}, {@code DELETE}) with {@code 405 Method Not Allowed}
 * ({@code REQ-F-007}).</p>
 */
@RestController
@RequestMapping("/api/v1/consumption-entries")
public class ConsumptionController {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumptionController.class);

    private final ConsumptionService consumptionService;
    private final JwtRequestAuthenticator jwtRequestAuthenticator;

    /**
     * Constructs the consumption controller.
     *
     * @param consumptionService      service managing append-only consumption entries
     * @param jwtRequestAuthenticator Bearer JWT extractor and verifier
     */
    public ConsumptionController(
            ConsumptionService consumptionService,
            JwtRequestAuthenticator jwtRequestAuthenticator
    ) {
        this.consumptionService = consumptionService;
        this.jwtRequestAuthenticator = jwtRequestAuthenticator;
    }

    /**
     * Records a new immutable consumption entry for the authenticated user and atomically
     * removes the beer from the user's wishlist if present.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param request    consumption payload specifying beer, packaging, quantity, and timestamp
     * @return {@code 201 Created} with {@link ConsumptionEntryResponse}
     */
    @PostMapping
    public ResponseEntity<ConsumptionEntryResponse> recordConsumption(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ConsumptionEntryCreateRequest request
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "201");
        ConsumptionEntryResponse created = consumptionService.recordConsumption(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists paginated consumption entries belonging strictly to the authenticated user.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param page       zero-based page index
     * @param size       number of items per page (1..100)
     * @return {@code 200 OK} with {@link ConsumptionPagedResponse}
     */
    @GetMapping
    public ResponseEntity<ConsumptionPagedResponse> listEntries(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "200");
        return ResponseEntity.ok(consumptionService.listEntries(userId, page, size));
    }

    /**
     * Retrieves a single consumption entry owned by the authenticated user.
     * Returns {@code 404 Not Found} for non-existent or foreign-owned IDs ({@code REQ-NF-005}).
     *
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param id         UUID of the consumption entry
     * @return {@code 200 OK} with {@link ConsumptionEntryResponse}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ConsumptionEntryResponse> getEntryById(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") UUID id
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "200");
        return ResponseEntity.ok(consumptionService.getEntryById(userId, id));
    }

    /**
     * Rejects HTTP {@code PUT} requests to uphold append-only log immutability.
     *
     * @param id      target consumption entry identifier
     * @param request HTTP servlet request for security audit logging
     * @return never returns normally; always throws {@code 405 METHOD_NOT_ALLOWED}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> rejectPut(@PathVariable("id") String id, HttpServletRequest request) {
        throw appendOnlyViolation(request);
    }

    /**
     * Rejects HTTP {@code PATCH} requests to uphold append-only log immutability.
     *
     * @param id      target consumption entry identifier
     * @param request HTTP servlet request for security audit logging
     * @return never returns normally; always throws {@code 405 METHOD_NOT_ALLOWED}
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> rejectPatch(@PathVariable("id") String id, HttpServletRequest request) {
        throw appendOnlyViolation(request);
    }

    /**
     * Rejects HTTP {@code DELETE} requests to uphold append-only log immutability.
     *
     * @param id      target consumption entry identifier
     * @param request HTTP servlet request for security audit logging
     * @return never returns normally; always throws {@code 405 METHOD_NOT_ALLOWED}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> rejectDelete(@PathVariable("id") String id, HttpServletRequest request) {
        throw appendOnlyViolation(request);
    }

    private ApiException appendOnlyViolation(HttpServletRequest request) {
        LOG.warn("Mutation attempt on immutable consumption log entry rejected",
                kv("event", "security_violation_audit"),
                kv("http_method", request.getMethod()),
                kv("endpoint", request.getRequestURI()),
                kv("violation_type", "APPEND_ONLY_VIOLATION"),
                kv("status_code", 405));
        String issue = "Mutating HTTP methods (PUT, PATCH, DELETE) are strictly forbidden";
        return new ApiException(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "Consumption entries are immutable (Append-Only) and cannot be updated or deleted",
                List.of(new ErrorDetail("http_method", issue))
        );
    }
}
