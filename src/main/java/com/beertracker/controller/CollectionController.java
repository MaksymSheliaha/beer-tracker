package com.beertracker.controller;

import com.beertracker.dto.CollectionPagedResponse;
import com.beertracker.security.JwtRequestAuthenticator;
import com.beertracker.service.CollectionService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller exposing the authenticated user's virtual {@code Tasted Collection}
 * projection derived from recorded consumption entries ({@code REQ-F-009}, {@code REQ-NF-005}).
 */
@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final CollectionService collectionService;
    private final JwtRequestAuthenticator jwtRequestAuthenticator;

    /**
     * Constructs the collection controller.
     *
     * @param collectionService       service building tasted beer aggregations
     * @param jwtRequestAuthenticator Bearer JWT extractor and verifier
     */
    public CollectionController(
            CollectionService collectionService,
            JwtRequestAuthenticator jwtRequestAuthenticator
    ) {
        this.collectionService = collectionService;
        this.jwtRequestAuthenticator = jwtRequestAuthenticator;
    }

    /**
     * Lists unique beers consumed by the authenticated user with aggregated volumes and ratings.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param page       zero-based page index
     * @param size       number of items per page (1..100)
     * @return {@code 200 OK} with paginated {@link CollectionPagedResponse}
     */
    @GetMapping("/tasted")
    public ResponseEntity<CollectionPagedResponse> getTastedCollection(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK)
                .body(collectionService.getTastedCollection(userId, page, size));
    }
}
