package com.beertracker.service;

import com.beertracker.dto.BeerPackagingCreateRequest;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.dto.BeerVersionCreateRequest;
import com.beertracker.dto.BeerVersionRejectRequest;
import com.beertracker.dto.BeerVersionResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.ModerationDecisionRequest;
import com.beertracker.dto.ModerationDecisionResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.BeerMapper;
import com.beertracker.mapper.BeerVersionMapper;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.model.BeerVersion;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.BeerVersionRepository;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.WishlistRepository;
import com.beertracker.validation.DomainValidationPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);
    private static final String PLACEHOLDER_BEER_NAME = "Видалений продукт";
    private static final Set<String> ALLOWED_MODERATION_DECISIONS = Set.of("APPROVE", "PERMANENT_DELETE");

    private final BeerRepository beerRepository;
    private final BeerVersionRepository beerVersionRepository;
    private final ConsumptionRepository consumptionRepository;
    private final WishlistRepository wishlistRepository;
    private final BeerMapper beerMapper;
    private final BeerVersionMapper beerVersionMapper;

    public ModerationService(
            BeerRepository beerRepository,
            BeerVersionRepository beerVersionRepository,
            ConsumptionRepository consumptionRepository,
            WishlistRepository wishlistRepository,
            BeerMapper beerMapper,
            BeerVersionMapper beerVersionMapper
    ) {
        this.beerRepository = beerRepository;
        this.beerVersionRepository = beerVersionRepository;
        this.consumptionRepository = consumptionRepository;
        this.wishlistRepository = wishlistRepository;
        this.beerMapper = beerMapper;
        this.beerVersionMapper = beerVersionMapper;
    }

    public synchronized void softDeleteBeer(UUID beerId) {
        Beer beer = beerRepository.findById(beerId)
                .filter(b -> !"PLACEHOLDER".equals(b.status()) && !"DELETED".equals(b.status()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "Resource requested at /beers/" + beerId + " was not found",
                        List.of(new ErrorDetail("id", "Requested resource with the specified identifier was not found"))
                ));

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

        log.info("Beer soft-deleted to PLACEHOLDER by moderator",
                kv("event", "beer_soft_deleted"),
                kv("beer_id", beerId.toString()),
                kv("new_status", "PLACEHOLDER"),
                kv("status", "204"));
    }

    public synchronized BeerVersionResponse verifyBeerVersionByModerator(UUID versionId) {
        synchronized (beerVersionRepository.getLockForVersion(versionId)) {
            BeerVersion version = beerVersionRepository.findById(versionId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "NOT_FOUND",
                            "Resource requested at /beer-versions/" + versionId + "/verify was not found",
                            List.of(new ErrorDetail("id", "Requested resource with the specified identifier was not found"))
                    ));

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

            log.info("Beer version manually verified by moderator",
                    kv("event", "version_verification_completed"),
                    kv("version_id", versionId.toString()),
                    kv("beer_id", version.beerId().toString()),
                    kv("previous_status", version.status()),
                    kv("new_status", "VERIFIED"),
                    kv("trigger", "MODERATOR_OVERRIDE"),
                    kv("status", "200"));

            return beerVersionMapper.toResponse(verifiedVersion);
        }
    }

    public synchronized BeerVersionResponse rejectBeerVersionByModerator(
            UUID versionId,
            BeerVersionRejectRequest request
    ) {
        synchronized (beerVersionRepository.getLockForVersion(versionId)) {
            BeerVersion version = beerVersionRepository.findById(versionId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "NOT_FOUND",
                            "Resource requested at /beer-versions/" + versionId + "/reject was not found",
                            List.of(new ErrorDetail("id", "Requested resource with the specified identifier was not found"))
                    ));

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

            log.info("Beer version manually rejected by moderator",
                    kv("event", "version_verification_completed"),
                    kv("version_id", versionId.toString()),
                    kv("beer_id", version.beerId().toString()),
                    kv("previous_status", version.status()),
                    kv("new_status", "REJECTED"),
                    kv("trigger", "MODERATOR_OVERRIDE"),
                    kv("reason", request != null ? request.reason() : null),
                    kv("status", "200"));

            return beerVersionMapper.toResponse(rejectedVersion);
        }
    }

    public BeerPagedResponse listModerationQueue(int page, int size) {
        if (!DomainValidationPredicates.IS_VALID_PAGE_INDEX.test(page)
                || !DomainValidationPredicates.IS_VALID_PAGE_SIZE.test(size)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Invalid pagination parameters",
                    List.of(new ErrorDetail("size", "Must be between 1 and 100"))
            );
        }

        List<BeerResponse> rejectedBeers = beerRepository.findByStatus("REJECTED").stream()
                .map(beerMapper::toResponse)
                .toList();

        return beerMapper.toPagedResponse(rejectedBeers, page, size);
    }

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

        Beer beer = beerRepository.findById(beerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "Resource requested at /moderation-items/" + beerId + "/decision was not found",
                        List.of(new ErrorDetail("id", "Requested resource with the specified identifier was not found"))
                ));

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
        log.info("Moderation queue item resolved",
                kv("event", "moderation_decision_processed"),
                kv("beer_id", beerId.toString()),
                kv("decision", request.decision()),
                kv("comment", request.comment()),
                kv("status", "200"));

        return new ModerationDecisionResponse(beerId, request.decision(), processedAt);
    }

    private void applyVerifiedVersionToCatalog(BeerVersion version) {
        beerRepository.findById(version.beerId()).ifPresent(existingBeer -> {
            BeerVersionCreateRequest p = version.payload();
            List<BeerPackaging> updatedPackagings = new ArrayList<>(existingBeer.packagings());
            if (p != null && p.packagings() != null && !p.packagings().isEmpty()) {
                for (BeerPackagingCreateRequest pkgReq : p.packagings()) {
                    updatedPackagings.add(beerMapper.toPackagingEntity(existingBeer.id(), pkgReq));
                }
            }

            Beer updatedBeer = new Beer(
                    existingBeer.id(),
                    p != null && p.name() != null ? p.name().trim() : existingBeer.name(),
                    p != null && p.brewery() != null ? p.brewery().trim() : existingBeer.brewery(),
                    p != null && p.style() != null ? p.style().trim() : existingBeer.style(),
                    p != null && p.abv() != null ? p.abv() : existingBeer.abv(),
                    p != null && p.beerType() != null ? p.beerType() : existingBeer.beerType(),
                    p != null && p.description() != null ? p.description() : existingBeer.description(),
                    p != null && p.country() != null ? p.country() : existingBeer.country(),
                    p != null && p.imageUrl() != null ? p.imageUrl() : existingBeer.imageUrl(),
                    "VERIFIED",
                    existingBeer.ratingAvg(),
                    existingBeer.reviewsCount(),
                    List.copyOf(updatedPackagings),
                    existingBeer.createdAt()
            );
            beerRepository.save(updatedBeer);
        });
    }
}
