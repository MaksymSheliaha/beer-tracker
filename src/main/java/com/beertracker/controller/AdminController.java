package com.beertracker.controller;

import com.beertracker.dto.BeerMergeRequest;
import com.beertracker.dto.BeerMergeResponse;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerVersionRejectRequest;
import com.beertracker.dto.BeerVersionResponse;
import com.beertracker.dto.ModerationDecisionRequest;
import com.beertracker.dto.ModerationDecisionResponse;
import com.beertracker.security.JwtRequestAuthenticator;
import com.beertracker.service.MergeService;
import com.beertracker.service.ModerationService;
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

import java.util.UUID;

/**
 * REST controller exposing moderator-only catalog administration, duplicate merging,
 * and moderation queue endpoints under {@code /api/v1}.
 *
 * <p>All operations strictly require a valid Bearer JWT with the {@code MODERATOR} role
 * ({@code REQ-F-010}, {@code REQ-F-011}, {@code REQ-F-013}, {@code REQ-F-014}).</p>
 */
@RestController
@RequestMapping("/api/v1")
public class AdminController {

    private final MergeService mergeService;
    private final ModerationService moderationService;
    private final JwtRequestAuthenticator jwtRequestAuthenticator;

    /**
     * Constructs the administrative controller with required services and authenticator.
     *
     * @param mergeService            service handling transactional duplicate beer merges
     * @param moderationService       service handling soft deletion and moderation decisions
     * @param jwtRequestAuthenticator Bearer JWT extractor and moderator role verifier
     */
    public AdminController(
            MergeService mergeService,
            ModerationService moderationService,
            JwtRequestAuthenticator jwtRequestAuthenticator
    ) {
        this.mergeService = mergeService;
        this.moderationService = moderationService;
        this.jwtRequestAuthenticator = jwtRequestAuthenticator;
    }

    /**
     * Soft-deletes a catalog beer by replacing its public identity with {@code Placeholder Beer}.
     *
     * @param beerId     UUID of the target beer product
     * @param authHeader HTTP {@code Authorization} header carrying a moderator Bearer token
     * @return {@code 204 No Content} on successful soft deletion
     */
    @DeleteMapping("/beers/{id}")
    public ResponseEntity<Void> softDeleteBeer(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        jwtRequestAuthenticator.requireModeratorId(authHeader);
        MDC.put("status", "204");

        moderationService.softDeleteBeer(beerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Atomically merges a duplicate source beer into a canonical target beer.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a moderator Bearer token
     * @param request    merge request specifying {@code source_beer_id} and {@code target_beer_id}
     * @return {@code 200 OK} with {@link BeerMergeResponse} migration counts
     */
    @PostMapping("/merges")
    public ResponseEntity<BeerMergeResponse> mergeBeers(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BeerMergeRequest request
    ) {
        UUID moderatorId = jwtRequestAuthenticator.requireModeratorId(authHeader);
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(mergeService.mergeBeers(moderatorId, request));
    }

    /**
     * Immediately verifies a pending {@code BeerVersion} draft, bypassing community voting.
     *
     * @param versionId  UUID of the {@code BeerVersion} to verify
     * @param authHeader HTTP {@code Authorization} header carrying a moderator Bearer token
     * @return {@code 200 OK} with the updated {@link BeerVersionResponse}
     */
    @PostMapping("/beer-versions/{id}/verify")
    public ResponseEntity<BeerVersionResponse> verifyBeerVersion(
            @PathVariable("id") UUID versionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        jwtRequestAuthenticator.requireModeratorId(authHeader);
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK)
                .body(moderationService.verifyBeerVersionByModerator(versionId));
    }

    /**
     * Immediately rejects a {@code BeerVersion} draft with an optional moderation reason.
     *
     * @param versionId  UUID of the {@code BeerVersion} to reject
     * @param authHeader HTTP {@code Authorization} header carrying a moderator Bearer token
     * @param request    optional rejection reason payload
     * @return {@code 200 OK} with the updated {@link BeerVersionResponse}
     */
    @PostMapping("/beer-versions/{id}/reject")
    public ResponseEntity<BeerVersionResponse> rejectBeerVersion(
            @PathVariable("id") UUID versionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) BeerVersionRejectRequest request
    ) {
        jwtRequestAuthenticator.requireModeratorId(authHeader);
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK)
                .body(moderationService.rejectBeerVersionByModerator(versionId, request));
    }

    /**
     * Lists rejected beer products awaiting manual moderator audit.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a moderator Bearer token
     * @param page       zero-based page index
     * @param size       number of items per page (1..100)
     * @return {@code 200 OK} with paginated rejected {@link BeerPagedResponse}
     */
    @GetMapping("/moderation-items/queue")
    public ResponseEntity<BeerPagedResponse> listModerationQueue(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        jwtRequestAuthenticator.requireModeratorId(authHeader);
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(moderationService.listModerationQueue(page, size));
    }

    /**
     * Applies a final moderator decision ({@code APPROVE} or {@code PERMANENT_DELETE})
     * to a rejected beer product in the moderation queue.
     *
     * @param beerId     UUID of the queued beer product
     * @param authHeader HTTP {@code Authorization} header carrying a moderator Bearer token
     * @param request    moderation decision and optional comment
     * @return {@code 200 OK} with {@link ModerationDecisionResponse}
     */
    @PostMapping("/moderation-items/{id}/decision")
    public ResponseEntity<ModerationDecisionResponse> resolveModerationQueueItem(
            @PathVariable("id") UUID beerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ModerationDecisionRequest request
    ) {
        jwtRequestAuthenticator.requireModeratorId(authHeader);
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK)
                .body(moderationService.resolveModerationQueueItem(beerId, request));
    }
}
