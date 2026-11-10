package com.beertracker.controller;

import com.beertracker.dto.BeerMergeRequest;
import com.beertracker.dto.BeerMergeResponse;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerVersionRejectRequest;
import com.beertracker.dto.BeerVersionResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.ModerationDecisionRequest;
import com.beertracker.dto.ModerationDecisionResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.security.JwtTokenProvider;
import com.beertracker.service.MergeService;
import com.beertracker.service.ModerationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AdminController {

    private final MergeService mergeService;
    private final ModerationService moderationService;
    private final JwtTokenProvider jwtTokenProvider;

    public AdminController(
            MergeService mergeService,
            ModerationService moderationService,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.mergeService = mergeService;
        this.moderationService = moderationService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @DeleteMapping("/beers/{id}")
    public ResponseEntity<Void> softDeleteBeer(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID moderatorId = requireModeratorRole(authHeader);
        MDC.put("user_id", moderatorId.toString());
        MDC.put("status", "204");

        moderationService.softDeleteBeer(beerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/merges")
    public ResponseEntity<BeerMergeResponse> mergeBeers(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BeerMergeRequest request
    ) {
        UUID moderatorId = requireModeratorRole(authHeader);
        MDC.put("user_id", moderatorId.toString());
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(mergeService.mergeBeers(request));
    }

    @PostMapping("/beer-versions/{id}/verify")
    public ResponseEntity<BeerVersionResponse> verifyBeerVersion(
            @PathVariable("id") UUID versionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID moderatorId = requireModeratorRole(authHeader);
        MDC.put("user_id", moderatorId.toString());
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(moderationService.verifyBeerVersionByModerator(versionId));
    }

    @PostMapping("/beer-versions/{id}/reject")
    public ResponseEntity<BeerVersionResponse> rejectBeerVersion(
            @PathVariable("id") UUID versionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) BeerVersionRejectRequest request
    ) {
        UUID moderatorId = requireModeratorRole(authHeader);
        MDC.put("user_id", moderatorId.toString());
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK)
                .body(moderationService.rejectBeerVersionByModerator(versionId, request));
    }

    @GetMapping("/moderation-items/queue")
    public ResponseEntity<BeerPagedResponse> listModerationQueue(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UUID moderatorId = requireModeratorRole(authHeader);
        MDC.put("user_id", moderatorId.toString());
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(moderationService.listModerationQueue(page, size));
    }

    @PostMapping("/moderation-items/{id}/decision")
    public ResponseEntity<ModerationDecisionResponse> resolveModerationQueueItem(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ModerationDecisionRequest request
    ) {
        UUID moderatorId = requireModeratorRole(authHeader);
        MDC.put("user_id", moderatorId.toString());
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK)
                .body(moderationService.resolveModerationQueueItem(beerId, request));
    }

    private UUID requireModeratorRole(String authHeader) {
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

        String role = claims.path("role").asText("USER");
        if (!"MODERATOR".equals(role)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "User does not have required MODERATOR role",
                    List.of(new ErrorDetail("role", "Access denied for role '" + role + "'"))
            );
        }

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
