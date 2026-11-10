package com.beertracker.controller;

import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.ReviewCreateRequest;
import com.beertracker.dto.ReviewResponse;
import com.beertracker.dto.ReviewUpdateRequest;
import com.beertracker.exception.ApiException;
import com.beertracker.security.JwtTokenProvider;
import com.beertracker.service.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beers/{id}/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final JwtTokenProvider jwtTokenProvider;

    public ReviewController(ReviewService reviewService, JwtTokenProvider jwtTokenProvider) {
        this.reviewService = reviewService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ReviewCreateRequest request
    ) {
        UUID userId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", userId.toString());
        MDC.put("status", "201");

        ReviewResponse created = reviewService.createReview(beerId, userId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/beers/" + beerId + "/reviews"))
                .body(created);
    }

    @PutMapping
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ReviewUpdateRequest request
    ) {
        UUID userId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", userId.toString());
        MDC.put("status", "200");

        ReviewResponse updated = reviewService.updateReview(beerId, userId, request);
        return ResponseEntity.status(HttpStatus.OK).body(updated);
    }

    @DeleteMapping
    public ResponseEntity<Void> archiveReview(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", userId.toString());
        MDC.put("status", "204");

        reviewService.archiveReview(beerId, userId);
        return ResponseEntity.noContent().build();
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
