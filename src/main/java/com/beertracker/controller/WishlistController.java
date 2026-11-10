package com.beertracker.controller;

import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.WishlistItemResponse;
import com.beertracker.dto.WishlistPagedResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.security.JwtTokenProvider;
import com.beertracker.service.WishlistService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wishlist-items")
public class WishlistController {

    private final WishlistService wishlistService;
    private final JwtTokenProvider jwtTokenProvider;

    public WishlistController(WishlistService wishlistService, JwtTokenProvider jwtTokenProvider) {
        this.wishlistService = wishlistService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping
    public ResponseEntity<WishlistPagedResponse> listWishlist(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UUID userId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", userId.toString());
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(wishlistService.listWishlist(userId, page, size));
    }

    @PostMapping("/{beer_id}")
    public ResponseEntity<WishlistItemResponse> addWishlistItem(
            @PathVariable("beer_id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", userId.toString());
        MDC.put("status", "201");

        WishlistItemResponse created = wishlistService.addToWishlist(userId, beerId);
        return ResponseEntity
                .created(URI.create("/api/v1/wishlist-items/" + beerId))
                .body(created);
    }

    @DeleteMapping("/{beer_id}")
    public ResponseEntity<Void> removeWishlistItem(
            @PathVariable("beer_id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", userId.toString());
        MDC.put("status", "204");

        wishlistService.removeFromWishlist(userId, beerId);
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
