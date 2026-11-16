package com.beertracker.controller;

import com.beertracker.dto.ReviewCreateRequest;
import com.beertracker.dto.ReviewResponse;
import com.beertracker.dto.ReviewUpdateRequest;
import com.beertracker.security.JwtRequestAuthenticator;
import com.beertracker.service.ReviewService;
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
import java.util.UUID;

/**
 * REST controller managing verified tasting {@code Review} resources for a specific beer
 * under {@code /api/v1/beers/{id}/reviews} ({@code REQ-F-008}).
 */
@RestController
@RequestMapping("/api/v1/beers/{id}/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final JwtRequestAuthenticator jwtRequestAuthenticator;

    /**
     * Constructs the review controller.
     *
     * @param reviewService           service enforcing verified consumption and single active review rules
     * @param jwtRequestAuthenticator Bearer JWT extractor and verifier
     */
    public ReviewController(
            ReviewService reviewService,
            JwtRequestAuthenticator jwtRequestAuthenticator
    ) {
        this.reviewService = reviewService;
        this.jwtRequestAuthenticator = jwtRequestAuthenticator;
    }

    /**
     * Creates a user's single active review for a beer after verifying prior consumption.
     *
     * @param beerId     UUID of the reviewed beer
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param request    rating (1.0..5.0 step 0.5) and optional review text
     * @return {@code 201 Created} with {@link ReviewResponse}
     */
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ReviewCreateRequest request
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "201");

        ReviewResponse created = reviewService.createReview(beerId, userId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/beers/" + beerId + "/reviews"))
                .body(created);
    }

    /**
     * Updates the authenticated user's active review for the specified beer.
     *
     * @param beerId     UUID of the reviewed beer
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param request    updated rating and optional review text
     * @return {@code 200 OK} with updated {@link ReviewResponse}
     */
    @PutMapping
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ReviewUpdateRequest request
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "200");

        ReviewResponse updated = reviewService.updateReview(beerId, userId, request);
        return ResponseEntity.status(HttpStatus.OK).body(updated);
    }

    /**
     * Soft-archives the authenticated user's active review and recalculates beer aggregates.
     *
     * @param beerId     UUID of the reviewed beer
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @return {@code 204 No Content} on successful archival
     */
    @DeleteMapping
    public ResponseEntity<Void> archiveReview(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "204");

        reviewService.archiveReview(beerId, userId);
        return ResponseEntity.noContent().build();
    }
}
