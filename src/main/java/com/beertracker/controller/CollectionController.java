package com.beertracker.controller;

import com.beertracker.dto.CollectionPagedResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.security.JwtTokenProvider;
import com.beertracker.service.CollectionService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final CollectionService collectionService;
    private final JwtTokenProvider jwtTokenProvider;

    public CollectionController(CollectionService collectionService, JwtTokenProvider jwtTokenProvider) {
        this.collectionService = collectionService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping("/tasted")
    public ResponseEntity<CollectionPagedResponse> getTastedCollection(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UUID userId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", userId.toString());
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(collectionService.getTastedCollection(userId, page, size));
    }

    private UUID extractAuthenticatedUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Full authentication is required to access this resource",
                    List.of(new ErrorDetail("Authorization", "Missing or invalid Bearer JWT token"))
            );
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        JsonNode claims = jwtTokenProvider.validateAndParseToken(token)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "Full authentication is required to access this resource",
                        List.of(new ErrorDetail("Authorization", "Missing or invalid Bearer JWT token"))
                ));
        try {
            return UUID.fromString(claims.path("sub").asText());
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Invalid subject in JWT token",
                    List.of(new ErrorDetail("Authorization", "Invalid user identifier in token"))
            );
        }
    }
}
