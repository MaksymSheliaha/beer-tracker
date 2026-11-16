package com.beertracker.service;

import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.dto.BeerVersionRejectRequest;
import com.beertracker.dto.BeerVersionResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.ModerationDecisionRequest;
import com.beertracker.dto.ModerationDecisionResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.BeerMapper;
import com.beertracker.mapper.BeerVersionMapper;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerVersion;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.BeerVersionRepository;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.WishlistRepository;
import com.beertracker.validation.PaginationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Service handling moderator catalog soft deletion ({@code Placeholder Beer}), manual
 * {@link BeerVersion} approval/rejection, and moderation queue resolution
 * ({@code REQ-F-010}, {@code REQ-F-013}, {@code REQ-F-014}).
 */
@Service
public class ModerationService {

    private static final Logger LOG = LoggerFactory.getLogger(ModerationService.class);
    private static final String PLACEHOLDER_BEER_NAME = "Видалений продукт";
    private static final Set<String> ALLOWED_MODERATION_DECISIONS = Set.of("APPROVE", "PERMANENT_DELETE");

    private final BeerRepository beerRepository;
    private final BeerVersionRepository beerVersionRepository;
    private final ConsumptionRepository consumptionRepository;
    private final WishlistRepository wishlistRepository;
    private final BeerMapper beerMapper;
    private final BeerVersionMapper beerVersionMapper;
    private final ReadCommittedTxExecutor txExecutor;

    /**
     * Constructs the moderation service.
     *
     * @param beerRepository        repository storing catalog beers
     * @param beerVersionRepository repository storing proposed versions and locks
     * @param consumptionRepository repository updating historical placeholder display names
     * @param wishlistRepository    repository cleaning up deleted product references
     * @param beerMapper            mapper applying verified payloads and paginating catalog entries
     * @param beerVersionMapper     mapper converting {@link BeerVersion} responses
     * @param txExecutor            ACID {@code READ_COMMITTED} transaction executor
     */
    public ModerationService(
            BeerRepository beerRepository,
            BeerVersionRepository beerVersionRepository,
            ConsumptionRepository consumptionRepository,
            WishlistRepository wishlistRepository,
            BeerMapper beerMapper,
            BeerVersionMapper beerVersionMapper,
            ReadCommittedTxExecutor txExecutor
    ) {
        this.beerRepository = beerRepository;
        this.beerVersionRepository = beerVersionRepository;
        this.consumptionRepository = consumptionRepository;
        this.wishlistRepository = wishlistRepository;
        this.beerMapper = beerMapper;
        this.beerVersionMapper = beerVersionMapper;
        this.txExecutor = txExecutor;
    }

    /**
     * Marks an active catalog beer as {@code PLACEHOLDER}, clears wishlist references, and updates
     * historical consumption labels to {@code "Видалений продукт"} without deleting logs
     * ({@code AC-010.1}, {@code AC-010.2}).
     *
     * @param beerId UUID of the catalog beer to soft-delete
     * @throws ApiException with {@code 404 NOT_FOUND} if the beer does not exist or is already inactive
     */
    public synchronized void softDeleteBeer(UUID beerId) {
        txExecutor.executeVoid(() -> {
            Beer beer = beerRepository.findActiveById(beerId)
                    .orElseThrow(() -> {
                        String issue = "Requested resource with the specified identifier was not found";
                        return new ApiException(
                                HttpStatus.NOT_FOUND,
                                "NOT_FOUND",
                                "Resource requested at /beers/" + beerId + " was not found",
                                List.of(new ErrorDetail("id", issue))
                        );
                    });

            Beer placeholderBeer = new Beer(
                    beer.id(),
                    PLACEHOLDER_BEER_NAME,
                    beer.brewery(),
                    beer.style(),
                    beer.abv(),
                    beer.beerType(),
                    beer.description(),
                    beer.country(),
                    beer.imageUrl(),
                    "PLACEHOLDER",
                    beer.ratingAvg(),
                    beer.reviewsCount(),
                    beer.packagings(),
                    beer.createdAt()
            );
            beerRepository.save(placeholderBeer);
            wishlistRepository.removeAllByBeer(beerId);
            consumptionRepository.updateBeerNameForBeerId(beerId, PLACEHOLDER_BEER_NAME);

            LOG.info("Beer soft-deleted to PLACEHOLDER by moderator",
                    kv("event", "beer_soft_deleted"),
                    kv("beer_id", beerId.toString()),
                    kv("new_status", "PLACEHOLDER"),
                    kv("status", "204"));
        });
    }

    /**
     * Immediately promotes a {@link BeerVersion} draft to {@code VERIFIED} and applies its
     * attributes to the canonical catalog entry ({@code AC-013.1}).
     *
     * @param versionId version draft UUID
     * @return updated {@link BeerVersionResponse}
     * @throws ApiException with {@code 404 NOT_FOUND} if the version does not exist
     */
    public synchronized BeerVersionResponse verifyBeerVersionByModerator(UUID versionId) {
        synchronized (beerVersionRepository.getLockForVersion(versionId)) {
            return txExecutor.execute(() -> {
                BeerVersion version = beerVersionRepository.findById(versionId)
                        .orElseThrow(() -> {
                            String issue = "Requested resource with the specified identifier was not found";
                            return new ApiException(
                                    HttpStatus.NOT_FOUND,
                                    "NOT_FOUND",
                                    "Resource requested at /beer-versions/" + versionId + "/verify was not found",
                                    List.of(new ErrorDetail("id", issue))
                            );
                        });

                BeerVersion verifiedVersion = new BeerVersion(
                        version.id(),
                        version.beerId(),
                        version.authorId(),
                        version.payload(),
                        version.confirmVotes(),
                        version.rejectVotes(),
                        "VERIFIED",
                        version.createdAt()
                );
                beerVersionRepository.save(verifiedVersion);
                applyVerifiedVersionToCatalog(verifiedVersion);

                LOG.info("Beer version manually verified by moderator",
                        kv("event", "version_verification_completed"),
                        kv("version_id", versionId.toString()),
                        kv("beer_id", version.beerId().toString()),
                        kv("previous_status", version.status()),
                        kv("new_status", "VERIFIED"),
                        kv("trigger", "MODERATOR_OVERRIDE"),
                        kv("status", "200"));

                return beerVersionMapper.toResponse(verifiedVersion);
            });
        }
    }

    /**
     * Immediately marks a {@link BeerVersion} draft as {@code REJECTED} and transitions any
     * still-{@code UNVERIFIED} parent beer into the moderation queue ({@code AC-013.2}).
     *
     * @param versionId version draft UUID
     * @param request   optional moderator rejection reason payload
     * @return updated {@link BeerVersionResponse}
     * @throws ApiException with {@code 404 NOT_FOUND} if the version does not exist
     */
    public synchronized BeerVersionResponse rejectBeerVersionByModerator(
            UUID versionId,
            BeerVersionRejectRequest request
    ) {
        synchronized (beerVersionRepository.getLockForVersion(versionId)) {
            return txExecutor.execute(() -> {
                BeerVersion version = beerVersionRepository.findById(versionId)
                        .orElseThrow(() -> {
                            String issue = "Requested resource with the specified identifier was not found";
                            return new ApiException(
                                    HttpStatus.NOT_FOUND,
                                    "NOT_FOUND",
                                    "Resource requested at /beer-versions/" + versionId + "/reject was not found",
                                    List.of(new ErrorDetail("id", issue))
                            );
                        });

                BeerVersion rejectedVersion = new BeerVersion(
                        version.id(),
                        version.beerId(),
                        version.authorId(),
                        version.payload(),
                        version.confirmVotes(),
                        version.rejectVotes(),
                        "REJECTED",
                        version.createdAt()
                );
                beerVersionRepository.save(rejectedVersion);

                beerRepository.findById(version.beerId()).ifPresent(beer -> {
                    if ("UNVERIFIED".equals(beer.status())) {
                        Beer rejectedBeer = new Beer(
                                beer.id(),
                                beer.name(),
                                beer.brewery(),
                                beer.style(),
                                beer.abv(),
                                beer.beerType(),
                                beer.description(),
                                beer.country(),
                                beer.imageUrl(),
                                "REJECTED",
                                beer.ratingAvg(),
                                beer.reviewsCount(),
                                beer.packagings(),
                                beer.createdAt()
                        );
                        beerRepository.save(rejectedBeer);
                    }
                });

                LOG.info("Beer version manually rejected by moderator",
                        kv("event", "version_verification_completed"),
                        kv("version_id", versionId.toString()),
                        kv("beer_id", version.beerId().toString()),
                        kv("previous_status", version.status()),
                        kv("new_status", "REJECTED"),
                        kv("trigger", "MODERATOR_OVERRIDE"),
                        kv("reason", request != null ? request.reason() : null),
                        kv("status", "200"));

                return beerVersionMapper.toResponse(rejectedVersion);
            });
        }
    }

    /**
     * Lists paginated beers currently in {@code REJECTED} status awaiting moderator review
     * ({@code AC-014.1}).
     *
     * @param page zero-based page index
     * @param size page size (1..100)
     * @return paginated {@link BeerPagedResponse} of queued beers
     */
    @Transactional(readOnly = true)
    public BeerPagedResponse listModerationQueue(int page, int size) {
        PaginationHelper.validate(page, size);

        List<BeerResponse> rejectedBeers = beerRepository.findByStatus("REJECTED").stream()
                .map(beerMapper::toResponse)
                .toList();

        return beerMapper.toPagedResponse(rejectedBeers, page, size);
    }

    /**
     * Resolves a queued beer product by either approving it to {@code VERIFIED} or permanently
     * deleting it from the repository ({@code AC-014.2}).
     *
     * @param beerId  queued beer UUID
     * @param request moderation decision payload ({@code APPROVE} or {@code PERMANENT_DELETE})
     * @return {@link ModerationDecisionResponse} recording the applied decision and timestamp
     * @throws ApiException with {@code 404 NOT_FOUND} if the beer does not exist,
     *                      or {@code 422 UNPROCESSABLE_ENTITY} for unsupported decision values
     */
    public synchronized ModerationDecisionResponse resolveModerationQueueItem(
            UUID beerId,
            ModerationDecisionRequest request
    ) {
        if (request == null
                || request.decision() == null
                || !ALLOWED_MODERATION_DECISIONS.contains(request.decision())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "decision must be APPROVE or PERMANENT_DELETE",
                    List.of(new ErrorDetail("decision", "Allowed values are APPROVE or PERMANENT_DELETE"))
            );
        }

        return txExecutor.execute(() -> {
            Beer beer = beerRepository.findById(beerId)
                    .orElseThrow(() -> {
                        String issue = "Requested resource with the specified identifier was not found";
                        return new ApiException(
                                HttpStatus.NOT_FOUND,
                                "NOT_FOUND",
                                "Resource requested at /moderation-items/" + beerId + "/decision was not found",
                                List.of(new ErrorDetail("id", issue))
                        );
                    });

            if ("APPROVE".equals(request.decision())) {
                Beer approvedBeer = new Beer(
                        beer.id(),
                        beer.name(),
                        beer.brewery(),
                        beer.style(),
                        beer.abv(),
                        beer.beerType(),
                        beer.description(),
                        beer.country(),
                        beer.imageUrl(),
                        "VERIFIED",
                        beer.ratingAvg(),
                        beer.reviewsCount(),
                        beer.packagings(),
                        beer.createdAt()
                );
                beerRepository.save(approvedBeer);
            } else {
                beerRepository.deletePermanently(beerId);
                wishlistRepository.removeAllByBeer(beerId);
            }

            Instant processedAt = Instant.now();
            LOG.info("Moderation queue item resolved",
                    kv("event", "moderation_decision_processed"),
                    kv("beer_id", beerId.toString()),
                    kv("decision", request.decision()),
                    kv("comment", request.comment()),
                    kv("status", "200"));

            return new ModerationDecisionResponse(beerId, request.decision(), processedAt);
        });
    }

    private void applyVerifiedVersionToCatalog(BeerVersion version) {
        beerRepository.findById(version.beerId()).ifPresent(existingBeer -> {
            Beer updatedBeer = beerMapper.mergeWithVersionPayload(existingBeer, version.payload(), "VERIFIED");
            beerRepository.save(updatedBeer);
        });
    }
}
