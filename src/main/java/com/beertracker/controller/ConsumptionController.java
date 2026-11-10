package com.beertracker.controller;

import com.beertracker.dto.ConsumptionEntryCreateRequest;
import com.beertracker.dto.ConsumptionEntryResponse;
import com.beertracker.dto.ConsumptionPagedResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.security.JwtTokenProvider;
import com.beertracker.service.ConsumptionService;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@RestController
@RequestMapping("/api/v1/consumption-entries")
public class ConsumptionController {

    private static final Logger log = LoggerFactory.getLogger(ConsumptionController.class);

    private final ConsumptionService consumptionService;
    private final JwtTokenProvider jwtTokenProvider;

    public ConsumptionController(
            ConsumptionService consumptionService,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.consumptionService = consumptionService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping
    public ResponseEntity<ConsumptionEntryResponse> recordConsumption(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ConsumptionEntryCreateRequest request
    ) {
        UUID userId = extractOptionalUserId(authHeader);
        if (userId != null) {
            MDC.put("user_id", userId.toString());
        }
        MDC.put("status", "201");
        ConsumptionEntryResponse created = consumptionService.recordConsumption(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<ConsumptionPagedResponse> listEntries(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(consumptionService.listEntries(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConsumptionEntryResponse> getEntryById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(consumptionService.getEntryById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> rejectPut(@PathVariable("id") String id, HttpServletRequest request) {
        throw appendOnlyViolation(request);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> rejectPatch(@PathVariable("id") String id, HttpServletRequest request) {
        throw appendOnlyViolation(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> rejectDelete(@PathVariable("id") String id, HttpServletRequest request) {
        throw appendOnlyViolation(request);
    }

    private UUID extractOptionalUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        Optional<JsonNode> claimsOpt = jwtTokenProvider.validateAndParseToken(token);
        if (claimsOpt.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(claimsOpt.get().path("sub").asText());
        } catch (Exception e) {
            return null;
        }
    }

    private ApiException appendOnlyViolation(HttpServletRequest request) {
        log.warn("Modification attempt on immutable consumption log entry rejected",
                kv("event", "security_violation_audit"),
                kv("http_method", request.getMethod()),
                kv("endpoint", request.getRequestURI()),
                kv("violation_type", "APPEND_ONLY_VIOLATION"),
                kv("status_code", 405));
        return new ApiException(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "Consumption entries are immutable (Append-Only) and cannot be updated or deleted",
                List.of(new ErrorDetail("http_method", "Mutating HTTP methods (PUT, PATCH, DELETE) are strictly forbidden"))
        );
    }
}
