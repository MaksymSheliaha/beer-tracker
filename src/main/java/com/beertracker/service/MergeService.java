package com.beertracker.service;

import com.beertracker.dto.BeerMergeRequest;
import com.beertracker.dto.BeerMergeResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.model.Review;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.ReviewRepository;
import com.beertracker.repository.WishlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Service performing transactional catalog duplicate merging ({@code Merge}) with packaging
 * mapping, consumption entry re-linking, review deduplication, and structured audit logging
 * ({@code REQ-F-011}, {@code REQ-NF-007}, {@code REQ-NF-011}).
 */
@Service
public class MergeService {

    private static final Logger LOG = LoggerFactory.getLogger(MergeService.class);

    private final BeerRepository beerRepository;
    private final ConsumptionRepository consumptionRepository;
    private final ReviewRepository reviewRepository;
    private final WishlistRepository wishlistRepository;
    private final ReadCommittedTxExecutor txExecutor;

    /**
     * Constructs the catalog merge service.
     *
     * @param beerRepository        repository storing catalog beers and packagings
     * @param consumptionRepository repository storing consumption entries to re-link
     * @param reviewRepository      repository storing reviews to migrate/archive
     * @param wishlistRepository    repository storing wishlist items to clean up
     * @param txExecutor            ACID {@code READ_COMMITTED} transaction executor
     */
    public MergeService(
            BeerRepository beerRepository,
            ConsumptionRepository consumptionRepository,
            ReviewRepository reviewRepository,
            WishlistRepository wishlistRepository,
            ReadCommittedTxExecutor txExecutor
    ) {
        this.beerRepository = beerRepository;
        this.consumptionRepository = consumptionRepository;
        this.reviewRepository = reviewRepository;
        this.wishlistRepository = wishlistRepository;
        this.txExecutor = txExecutor;
    }

    /**
     * Atomically merges {@code source_beer_id} into {@code target_beer_id} within a
     * {@code READ_COMMITTED} ACID transaction ({@code AC-011.1}..{@code AC-011.4}).
     *
     * @param moderatorId authenticated moderator UUID executing the merge
     * @param request     merge request identifying source duplicate and canonical target beers
     * @return {@link BeerMergeResponse} detailing migrated consumptions, packagings, and archived reviews
     * @throws ApiException with {@code 400 BAD_REQUEST} if IDs are missing,
     *                      {@code 404 NOT_FOUND} if either beer is missing or inactive,
     *                      or {@code 422 UNPROCESSABLE_ENTITY} on self-merge attempts
     */
    public synchronized BeerMergeResponse mergeBeers(UUID moderatorId, BeerMergeRequest request) {
        long startNs = System.nanoTime();
        if (request == null || request.sourceBeerId() == null || request.targetBeerId() == null) {
            String issue = "Both source_beer_id and target_beer_id must be provided";
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "source_beer_id and target_beer_id are required",
                    List.of(new ErrorDetail("source_beer_id", issue))
            );
        }

        UUID sourceBeerId = request.sourceBeerId();
        UUID targetBeerId = request.targetBeerId();

        if (sourceBeerId.equals(targetBeerId)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CANNOT_MERGE_BEER_INTO_ITSELF",
                    "Source beer ID and Target beer ID must be distinct",
                    List.of(new ErrorDetail("target_beer_id", "Target beer cannot be identical to source beer"))
            );
        }

        return txExecutor.execute(() -> {
            Beer sourceBeer = beerRepository.findActiveById(sourceBeerId)
                    .orElseThrow(() -> {
                        String issue = "Source beer with specified identifier was not found";
                        return new ApiException(
                                HttpStatus.NOT_FOUND,
                                "NOT_FOUND",
                                "Resource requested at /merges was not found",
                                List.of(new ErrorDetail("source_beer_id", issue))
                        );
                    });

            Beer targetBeer = beerRepository.findActiveById(targetBeerId)
                    .orElseThrow(() -> {
                        String issue = "Target beer with specified identifier was not found";
                        return new ApiException(
                                HttpStatus.NOT_FOUND,
                                "NOT_FOUND",
                                "Resource requested at /merges was not found",
                                List.of(new ErrorDetail("target_beer_id", issue))
                        );
                    });

            List<BeerPackaging> sourcePackagingsSnapshot = sourceBeer.packagings().stream()
                    .map(p -> new BeerPackaging(p.id(), p.beerId(), p.volumeMl(), p.packageType(), p.barcode()))
                    .toList();

            // Mark sourceBeer as MERGED first so its barcodes are released from the unique constraint
            Beer mergedSourceBeer = new Beer(
                    sourceBeer.id(),
                    sourceBeer.name(),
                    sourceBeer.brewery(),
                    sourceBeer.style(),
                    sourceBeer.abv(),
                    sourceBeer.beerType(),
                    sourceBeer.description(),
                    sourceBeer.country(),
                    sourceBeer.imageUrl(),
                    "MERGED",
                    sourceBeer.ratingAvg(),
                    sourceBeer.reviewsCount(),
                    sourcePackagingsSnapshot,
                    sourceBeer.createdAt()
            );
            beerRepository.saveAndFlush(mergedSourceBeer);

            List<BeerPackaging> updatedTargetPackagings = new ArrayList<>(targetBeer.packagings());
            Map<UUID, UUID> packagingIdMapping = new HashMap<>();
            int migratedPackagingsCount = 0;

            for (BeerPackaging sourcePkg : sourcePackagingsSnapshot) {
                Optional<BeerPackaging> equivalentTargetPkg = updatedTargetPackagings.stream()
                        .filter(tp -> tp.packageType().equals(sourcePkg.packageType())
                                && tp.volumeMl().equals(sourcePkg.volumeMl()))
                        .findFirst();

                if (equivalentTargetPkg.isPresent()) {
                    packagingIdMapping.put(sourcePkg.id(), equivalentTargetPkg.get().id());
                } else {
                    BeerPackaging migratedPkg = new BeerPackaging(
                            UUID.randomUUID(),
                            targetBeerId,
                            sourcePkg.volumeMl(),
                            sourcePkg.packageType(),
                            sourcePkg.barcode()
                    );
                    updatedTargetPackagings.add(migratedPkg);
                    packagingIdMapping.put(sourcePkg.id(), migratedPkg.id());
                    migratedPackagingsCount++;
                }
            }

            int migratedConsumptionsCount = consumptionRepository.migrateConsumptions(
                    sourceBeerId,
                    targetBeerId,
                    targetBeer.name(),
                    packagingIdMapping
            );

            int archivedReviewsCount = reviewRepository.migrateAndDeduplicateReviews(sourceBeerId, targetBeerId);

            wishlistRepository.removeAllByBeer(sourceBeerId);

            List<Review> activeTargetReviews = reviewRepository.findActiveByBeer(targetBeerId);
            int reviewsCount = activeTargetReviews.size();
            Double ratingAvg = reviewsCount == 0
                    ? null
                    : Math.round(
                            activeTargetReviews.stream().mapToDouble(Review::rating).average().orElse(0.0) * 100.0
                    ) / 100.0;

            Beer updatedTargetBeer = new Beer(
                    targetBeer.id(),
                    targetBeer.name(),
                    targetBeer.brewery(),
                    targetBeer.style(),
                    targetBeer.abv(),
                    targetBeer.beerType(),
                    targetBeer.description(),
                    targetBeer.country(),
                    targetBeer.imageUrl(),
                    targetBeer.status(),
                    ratingAvg,
                    reviewsCount,
                    List.copyOf(updatedTargetPackagings),
                    targetBeer.createdAt()
            );
            beerRepository.save(updatedTargetBeer);

            double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;
            LOG.info("Catalog duplicate merge executed",
                    kv("event", "catalog_merge_executed"),
                    kv("moderator_id", moderatorId != null ? moderatorId.toString() : "unknown"),
                    kv("source_beer_id", sourceBeerId.toString()),
                    kv("target_beer_id", targetBeerId.toString()),
                    kv("migrated_consumptions_count", migratedConsumptionsCount),
                    kv("migrated_packagings_count", migratedPackagingsCount),
                    kv("archived_reviews_count", archivedReviewsCount),
                    kv("duration_ms", durationMs),
                    kv("status", "200"));

            return new BeerMergeResponse(
                    targetBeerId,
                    migratedConsumptionsCount,
                    migratedPackagingsCount,
                    archivedReviewsCount,
                    "MERGED"
            );
        });
    }
}
