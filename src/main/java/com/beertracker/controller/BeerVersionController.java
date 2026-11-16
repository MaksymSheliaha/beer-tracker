package com.beertracker.controller;

import com.beertracker.dto.BeerVersionCreateRequest;
import com.beertracker.dto.BeerVersionPagedResponse;
import com.beertracker.dto.BeerVersionResponse;
import com.beertracker.dto.BeerVersionVoteRequest;
import com.beertracker.dto.BeerVersionVoteResponse;
import com.beertracker.security.JwtRequestAuthenticator;
import com.beertracker.service.BeerVersionService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
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
import java.util.List;
import java.util.UUID;

/**
 * REST controller managing wiki-style {@code BeerVersion} drafts and community consensus voting
 * ({@code REQ-F-003}, {@code REQ-F-004}, {@code REQ-F-005}, {@code REQ-NF-009}).
 */
@RestController
@RequestMapping("/api/v1")
public class BeerVersionController {

    private final BeerVersionService beerVersionService;
    private final JwtRequestAuthenticator jwtRequestAuthenticator;

    /**
     * Constructs the versioning and voting controller.
     *
     * @param beerVersionService      service managing version drafts and consensus voting
     * @param jwtRequestAuthenticator Bearer JWT extractor and verifier
     */
    public BeerVersionController(
            BeerVersionService beerVersionService,
            JwtRequestAuthenticator jwtRequestAuthenticator
    ) {
        this.beerVersionService = beerVersionService;
        this.jwtRequestAuthenticator = jwtRequestAuthenticator;
    }

    /**
     * Proposes a new {@code UNVERIFIED} {@code BeerVersion} draft for an existing catalog beer.
     *
     * @param beerId     UUID of the target beer product
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param request    proposed attribute changes and/or additional packagings
     * @return {@code 201 Created} with {@link BeerVersionResponse}
     */
    @PostMapping("/beers/{id}/versions")
    public ResponseEntity<BeerVersionResponse> createBeerVersion(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BeerVersionCreateRequest request
    ) {
        UUID authorId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "201");

        BeerVersionResponse created = beerVersionService.createBeerVersion(beerId, authorId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/beer-versions/" + created.id()))
                .body(created);
    }

    /**
     * Lists all version drafts associated with the specified beer in chronological order.
     *
     * @param beerId UUID of the beer product
     * @return {@code 200 OK} with list of {@link BeerVersionResponse}
     */
    @GetMapping("/beers/{id}/versions")
    public ResponseEntity<List<BeerVersionResponse>> listBeerVersions(@PathVariable("id") UUID beerId) {
        MDC.put("status", "200");
        return ResponseEntity.status(HttpStatus.OK).body(beerVersionService.listBeerVersions(beerId));
    }

    /**
     * Lists version drafts filtered by verification status for authenticated community voters.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param status     target status filter ({@code UNVERIFIED}, {@code VERIFIED}, or {@code REJECTED})
     * @param page       zero-based page index
     * @param size       number of items per page (1..100)
     * @return {@code 200 OK} with {@link BeerVersionPagedResponse}
     */
    @GetMapping("/beer-versions")
    public ResponseEntity<BeerVersionPagedResponse> listVersionsByStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "status", defaultValue = "UNVERIFIED") String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "200");
        return ResponseEntity.status(HttpStatus.OK)
                .body(beerVersionService.listVersionsByStatus(status, page, size));
    }

    /**
     * Casts a {@code CONFIRM} or {@code REJECT} community vote on a proposed version draft.
     *
     * @param versionId  UUID of the target {@code BeerVersion}
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param request    vote payload containing {@code vote_type}
     * @return {@code 200 OK} with updated vote counters and status in {@link BeerVersionVoteResponse}
     */
    @PostMapping("/beer-versions/{id}/votes")
    public ResponseEntity<BeerVersionVoteResponse> voteBeerVersion(
            @PathVariable("id") UUID versionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BeerVersionVoteRequest request
    ) {
        UUID voterId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "200");

        BeerVersionVoteResponse response = beerVersionService.vote(versionId, voterId, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
