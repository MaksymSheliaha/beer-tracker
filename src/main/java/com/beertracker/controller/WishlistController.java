package com.beertracker.controller;

import com.beertracker.dto.WishlistItemResponse;
import com.beertracker.dto.WishlistPagedResponse;
import com.beertracker.security.JwtRequestAuthenticator;
import com.beertracker.service.WishlistService;
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
import java.util.UUID;

/**
 * REST controller managing the authenticated user's personal {@code Wishlist} under
 * {@code /api/v1/wishlist-items} ({@code REQ-F-009}, {@code REQ-NF-005}).
 */
@RestController
@RequestMapping("/api/v1/wishlist-items")
public class WishlistController {

    private final WishlistService wishlistService;
    private final JwtRequestAuthenticator jwtRequestAuthenticator;

    /**
     * Constructs the wishlist controller.
     *
     * @param wishlistService         service managing personal wishlist items
     * @param jwtRequestAuthenticator Bearer JWT extractor and verifier
     */
    public WishlistController(
            WishlistService wishlistService,
            JwtRequestAuthenticator jwtRequestAuthenticator
    ) {
        this.wishlistService = wishlistService;
        this.jwtRequestAuthenticator = jwtRequestAuthenticator;
    }

    /**
     * Lists paginated beers saved in the authenticated user's wishlist.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param page       zero-based page index
     * @param size       number of items per page (1..100)
     * @return {@code 200 OK} with {@link WishlistPagedResponse}
     */
    @GetMapping
    public ResponseEntity<WishlistPagedResponse> listWishlist(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(wishlistService.listWishlist(userId, page, size));
    }

    /**
     * Adds an active catalog beer to the authenticated user's wishlist.
     *
     * @param beerId     UUID of the target beer product
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @return {@code 201 Created} with {@link WishlistItemResponse}
     */
    @PostMapping("/{beer_id}")
    public ResponseEntity<WishlistItemResponse> addWishlistItem(
            @PathVariable("beer_id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "201");

        WishlistItemResponse created = wishlistService.addToWishlist(userId, beerId);
        return ResponseEntity
                .created(URI.create("/api/v1/wishlist-items/" + beerId))
                .body(created);
    }

    /**
     * Removes a beer from the authenticated user's wishlist.
     *
     * @param beerId     UUID of the beer to remove
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @return {@code 204 No Content} on removal, or {@code 404 Not Found} if absent
     */
    @DeleteMapping("/{beer_id}")
    public ResponseEntity<Void> removeWishlistItem(
            @PathVariable("beer_id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "204");

        wishlistService.removeFromWishlist(userId, beerId);
        return ResponseEntity.noContent().build();
    }
}
