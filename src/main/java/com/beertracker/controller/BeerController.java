package com.beertracker.controller;

import com.beertracker.dto.BeerCreateRequest;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.security.JwtRequestAuthenticator;
import com.beertracker.service.BeerService;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller managing canonical {@code Beer} catalog entries under {@code /api/v1/beers}.
 *
 * <p>Read operations are publicly accessible, while registering a new catalog entry requires
 * Bearer JWT authentication ({@code REQ-F-001}, {@code REQ-F-002}, {@code REQ-F-015}).</p>
 */
@RestController
@RequestMapping("/api/v1/beers")
public class BeerController {

    private final BeerService beerService;
    private final JwtRequestAuthenticator jwtRequestAuthenticator;

    /**
     * Constructs the catalog controller.
     *
     * @param beerService             catalog service managing beer products and search
     * @param jwtRequestAuthenticator Bearer JWT extractor and verifier
     */
    public BeerController(
            BeerService beerService,
            JwtRequestAuthenticator jwtRequestAuthenticator
    ) {
        this.beerService = beerService;
        this.jwtRequestAuthenticator = jwtRequestAuthenticator;
    }

    /**
     * Registers a new {@code UNVERIFIED} beer product with one or more packagings.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param request    beer creation payload with attributes and packaging options
     * @return {@code 201 Created} with {@code Location} header and {@link BeerResponse} body
     */
    @PostMapping
    public ResponseEntity<BeerResponse> createBeer(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BeerCreateRequest request
    ) {
        jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "201");
        BeerResponse created = beerService.createBeer(request);
        URI location = URI.create("/api/v1/beers/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Retrieves a single visible catalog beer by UUID, optionally restricting to verified data.
     *
     * @param id           UUID of the beer product
     * @param verifiedOnly when {@code true}, skips overlaying an active unverified draft version
     * @return {@code 200 OK} with {@link BeerResponse}
     */
    @GetMapping("/{id}")
    public ResponseEntity<BeerResponse> getBeerById(
            @PathVariable("id") UUID id,
            @RequestParam(name = "verified_only", defaultValue = "false") boolean verifiedOnly
    ) {
        return ResponseEntity.ok(beerService.getBeerById(id, verifiedOnly));
    }

    /**
     * Searches and filters visible catalog beers by text query and ABV range with pagination.
     *
     * @param query  optional substring matched against beer name or brewery (case-insensitive)
     * @param minAbv optional inclusive minimum ABV percentage in {@code [0.0, 100.0]}
     * @param maxAbv optional inclusive maximum ABV percentage in {@code [0.0, 100.0]}
     * @param page   zero-based page index
     * @param size   number of items per page (1..100)
     * @return {@code 200 OK} with paginated {@link BeerPagedResponse}
     */
    @GetMapping
    public ResponseEntity<BeerPagedResponse> listBeers(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "min_abv", required = false) Double minAbv,
            @RequestParam(name = "max_abv", required = false) Double maxAbv,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(beerService.listBeers(query, minAbv, maxAbv, page, size));
    }
}
