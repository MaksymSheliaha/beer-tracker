package com.beertracker.controller;

import com.beertracker.dto.BeerVersionCreateRequest;
import com.beertracker.dto.BeerVersionPagedResponse;
import com.beertracker.dto.BeerVersionResponse;
import com.beertracker.dto.BeerVersionVoteRequest;
import com.beertracker.dto.BeerVersionVoteResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.security.JwtTokenProvider;
import com.beertracker.service.BeerVersionService;
import com.fasterxml.jackson.databind.JsonNode;
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

@RestController
@RequestMapping("/api/v1")
public class BeerVersionController {

    private final BeerVersionService beerVersionService;
    private final JwtTokenProvider jwtTokenProvider;

    public BeerVersionController(BeerVersionService beerVersionService, JwtTokenProvider jwtTokenProvider) {
        this.beerVersionService = beerVersionService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/beers/{id}/versions")
    public ResponseEntity<BeerVersionResponse> createBeerVersion(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BeerVersionCreateRequest request
    ) {
        UUID authorId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", authorId.toString());
        MDC.put("status", "201");

        BeerVersionResponse created = beerVersionService.createBeerVersion(beerId, authorId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/beer-versions/" + created.id()))
                .body(created);
    }

    @GetMapping("/beers/{id}/versions")
    public ResponseEntity<List<BeerVersionResponse>> listBeerVersions(@PathVariable("id") UUID beerId) {
        MDC.put("status", "200");
        return ResponseEntity.status(HttpStatus.OK).body(beerVersionService.listBeerVersions(beerId));
    }

    @GetMapping("/beer-versions")
    public ResponseEntity<BeerVersionPagedResponse> listVersionsByStatus(
            @RequestParam(value = "status", defaultValue = "UNVERIFIED") String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        MDC.put("status", "200");
        return ResponseEntity.status(HttpStatus.OK).body(beerVersionService.listVersionsByStatus(status, page, size));
    }

    @PostMapping("/beer-versions/{id}/votes")
    public ResponseEntity<BeerVersionVoteResponse> voteBeerVersion(
            @PathVariable("id") UUID versionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BeerVersionVoteRequest request
    ) {
        UUID voterId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", voterId.toString());
        MDC.put("status", "200");

        BeerVersionVoteResponse response = beerVersionService.vote(versionId, voterId, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
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
